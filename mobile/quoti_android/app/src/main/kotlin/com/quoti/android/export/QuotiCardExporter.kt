package com.quoti.android.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.quoti.android.core.model.CardContentMode
import com.quoti.android.core.model.CardTone
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.hasMedia
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PngMimeType = "image/png"
private const val ExportDirectory = "quoti_exports"
private const val PicturesRelativePath = "Pictures/Quoti"
private const val ExportBitmapWidth = 1080
private const val CardPadding = 72f
private const val HeaderHeight = 88f
private const val SectionGap = 48f
private const val SmallGap = 20f
private const val RelatedPadding = 34f
private const val MinMediaHeight = 260f
private const val PlaceholderMediaAspectRatio = 1.85f
private const val MediaGridGap = 6f

object QuotiCardExporter {
    suspend fun writeCachePng(
        context: Context,
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
    ): Uri {
        val bitmap = renderCardBitmap(post, cardTone, contentMode)
        val fileName = quotiCardFileName(post.id)

        return try {
            withContext(Dispatchers.IO) {
                val exportDir = File(context.cacheDir, ExportDirectory)
                exportDir.mkdirs()

                val output = File(exportDir, fileName)
                output.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Unable to encode Quoti card PNG."
                    }
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    output,
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun writePicturesPng(
        context: Context,
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
    ): Uri {
        val bitmap = renderCardBitmap(post, cardTone, contentMode)
        val fileName = quotiCardFileName(post.id)

        return try {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val values =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, PngMimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, PicturesRelativePath)
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("Unable to create a MediaStore entry for the Quoti card.")

                runCatching {
                    resolver.openOutputStream(uri)?.use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            "Unable to encode Quoti card PNG."
                        }
                    } ?: error("Unable to open MediaStore output stream.")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                }.onFailure {
                    resolver.delete(uri, null, null)
                }.getOrThrow()

                uri
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun renderCardBitmap(
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
    ): Bitmap {
        val mediaBitmaps =
            if (contentMode == CardContentMode.WithMedia) {
                post.exportMediaSources()
                    .take(4)
                    .mapNotNull { source ->
                        fetchRemoteBitmap(source.url)?.let { bitmap ->
                            ExportMediaBitmap(
                                bitmap = bitmap,
                                isVideo = source.isVideo,
                            )
                        }
                    }
            } else {
                emptyList()
            }

        return try {
            withContext(Dispatchers.Default) {
                QuotiCardBitmapRenderer.render(
                    post = post,
                    cardTone = cardTone,
                    contentMode = contentMode,
                    mediaBitmaps = mediaBitmaps,
                )
            }
        } finally {
            mediaBitmaps.forEach { media -> media.bitmap.recycle() }
        }
    }

    private suspend fun fetchRemoteBitmap(imageUrl: String): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(imageUrl).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 7_000
                    connection.setRequestProperty("User-Agent", "Quoti Android")

                    if (connection.responseCode !in 200..299) {
                        return@runCatching null
                    }

                    connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}

private object QuotiCardBitmapRenderer {
    fun render(
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
        mediaBitmaps: List<ExportMediaBitmap>,
    ): Bitmap {
        val palette = RenderPalette.from(cardTone)
        val measure = measure(post, palette, contentMode, mediaBitmaps)
        val bitmap = Bitmap.createBitmap(ExportBitmapWidth, measure.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(palette.surface)
        drawCard(canvas, post, palette, measure, contentMode, mediaBitmaps)

        return bitmap
    }

    private fun measure(
        post: QuotiPost,
        palette: RenderPalette,
        contentMode: CardContentMode,
        mediaBitmaps: List<ExportMediaBitmap>,
    ): MeasuredCard {
        val contentWidth = ExportBitmapWidth - (CardPadding * 2).roundToInt()
        val authorWidth = (contentWidth - 220).coerceAtLeast(360)
        val contentPaint = textPaint(palette.textPrimary, 54f, Typeface.SERIF)
        val metadataPaint = textPaint(palette.textSecondary, 32f, Typeface.SANS_SERIF)
        val authorPaint = textPaint(palette.textPrimary, 34f, Typeface.SANS_SERIF, bold = true)
        val handlePaint = textPaint(palette.textSecondary, 31f, Typeface.SANS_SERIF)
        val brandPaint = textPaint(palette.brand, 40f, Typeface.SERIF, bold = true)

        val related = post.relatedPost?.let { relatedPost ->
            val relatedContentWidth = (contentWidth - RelatedPadding * 2).roundToInt()
            MeasuredRelatedPost(
                authorLayout =
                    textLayout(
                        text = relatedPost.authorLabel(),
                        paint = metadataPaint,
                        width = relatedContentWidth,
                        maxLines = 1,
                    ),
                contentLayout =
                    textLayout(
                        text = relatedPost.content,
                        paint = textPaint(palette.textPrimary, 34f, Typeface.SERIF),
                        width = relatedContentWidth,
                    ),
            )
        }
        val mediaHeight =
            if (contentMode == CardContentMode.WithMedia && post.hasMedia) {
                measureMediaHeight(contentWidth.toFloat(), mediaBitmaps)
            } else {
                0f
            }
        val authorNameLayout =
            textLayout(
                text = post.authorName,
                paint = authorPaint,
                width = authorWidth,
                maxLines = 1,
            )
        val authorHandleLayout =
            textLayout(
                text = post.authorHandle,
                paint = handlePaint,
                width = authorWidth,
                maxLines = 1,
            )
        val markLayout =
            textLayout(
                text = "Quoti",
                paint = brandPaint,
                width = 190,
                alignment = Layout.Alignment.ALIGN_OPPOSITE,
                maxLines = 1,
            )

        var height = CardPadding + HeaderHeight + SectionGap
        height += textLayout(post.content, contentPaint, contentWidth).height
        related?.let {
            height += SectionGap
            height += it.height
        }
        if (mediaHeight > 0f) {
            height += SectionGap
            height += mediaHeight
        }
        height += SectionGap
        height += 2f
        height += SmallGap
        height += max(authorNameLayout.height + 10 + authorHandleLayout.height, markLayout.height)
        height += CardPadding

        return MeasuredCard(
            dateLayout =
                textLayout(
                    text = formatDate(post.publishedAt ?: post.capturedAt),
                    paint = metadataPaint,
                    width = 320,
                    alignment = Layout.Alignment.ALIGN_OPPOSITE,
                    maxLines = 1,
                ),
            contentLayout = textLayout(post.content, contentPaint, contentWidth),
            relatedPost = related,
            mediaHeight = mediaHeight,
            authorNameLayout = authorNameLayout,
            authorHandleLayout = authorHandleLayout,
            markLayout = markLayout,
            height = ceil(height).toInt(),
        )
    }

    private fun drawCard(
        canvas: Canvas,
        post: QuotiPost,
        palette: RenderPalette,
        measure: MeasuredCard,
        contentMode: CardContentMode,
        mediaBitmaps: List<ExportMediaBitmap>,
    ) {
        val contentWidth = ExportBitmapWidth - CardPadding * 2
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
        var y = CardPadding

        fillPaint.color = withAlpha(palette.textPrimary, 0.12f)
        canvas.drawOval(RectF(CardPadding, y, CardPadding + 72f, y + 72f), fillPaint)
        drawCenteredText(
            canvas = canvas,
            text = post.platform.label,
            centerX = CardPadding + 36f,
            centerY = y + 36f,
            paint = textPaint(palette.textPrimary, 33f, Typeface.SANS_SERIF, bold = true),
        )
        drawLayout(
            canvas = canvas,
            layout = measure.dateLayout,
            x = ExportBitmapWidth - CardPadding - measure.dateLayout.width,
            y = y + ((72f - measure.dateLayout.height) / 2f),
        )

        y += HeaderHeight + SectionGap
        drawLayout(canvas, measure.contentLayout, CardPadding, y)
        y += measure.contentLayout.height

        measure.relatedPost?.let { relatedPost ->
            y += SectionGap
            val relatedHeight = relatedPost.height
            val relatedRect = RectF(CardPadding, y, CardPadding + contentWidth, y + relatedHeight)
            strokePaint.color = withAlpha(palette.textPrimary, 0.14f)
            canvas.drawRoundRect(relatedRect, 34f, 34f, strokePaint)
            var relatedY = y + RelatedPadding
            drawLayout(canvas, relatedPost.authorLayout, CardPadding + RelatedPadding, relatedY)
            relatedY += relatedPost.authorLayout.height + SmallGap
            drawLayout(canvas, relatedPost.contentLayout, CardPadding + RelatedPadding, relatedY)
            y += relatedHeight
        }

        if (contentMode == CardContentMode.WithMedia && measure.mediaHeight > 0f) {
            y += SectionGap
            val mediaRect = RectF(CardPadding, y, CardPadding + contentWidth, y + measure.mediaHeight)
            drawMedia(canvas, mediaBitmaps, mediaRect, palette)
            y += measure.mediaHeight
        }

        y += SectionGap
        strokePaint.color = withAlpha(palette.textPrimary, 0.14f)
        canvas.drawLine(CardPadding, y, CardPadding + contentWidth, y, strokePaint)
        y += SmallGap

        drawLayout(canvas, measure.authorNameLayout, CardPadding, y)
        drawLayout(
            canvas = canvas,
            layout = measure.authorHandleLayout,
            x = CardPadding,
            y = y + measure.authorNameLayout.height + 10f,
        )
        drawLayout(
            canvas = canvas,
            layout = measure.markLayout,
            x = ExportBitmapWidth - CardPadding - measure.markLayout.width,
            y = y,
        )
    }

    private fun measureMediaHeight(
        contentWidth: Float,
        mediaBitmaps: List<ExportMediaBitmap>,
    ): Float {
        if (mediaBitmaps.isEmpty()) {
            return max(MinMediaHeight, contentWidth / PlaceholderMediaAspectRatio)
        }

        if (mediaBitmaps.size == 1) {
            val bitmap = mediaBitmaps.first().bitmap
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            return max(MinMediaHeight, contentWidth / ratio.coerceIn(0.62f, 2.35f))
        }

        return if (mediaBitmaps.size == 2) {
            max(MinMediaHeight, contentWidth / 2f)
        } else {
            contentWidth
        }
    }

    private fun drawMedia(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
        palette: RenderPalette,
    ) {
        val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = withAlpha(palette.textPrimary, 0.08f)
            }
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = withAlpha(palette.textPrimary, 0.14f)
            }

        canvas.drawRoundRect(rect, 42f, 42f, fillPaint)

        if (mediaBitmaps.isEmpty()) {
            drawCenteredText(
                canvas = canvas,
                text = "Media",
                centerX = rect.centerX(),
                centerY = rect.centerY(),
                paint = textPaint(palette.textSecondary, 34f, Typeface.SANS_SERIF, bold = true),
            )
            canvas.drawRoundRect(rect, 42f, 42f, strokePaint)
            return
        }

        canvas.save()
        canvas.clipPath(
            Path().apply {
                addRoundRect(rect, 42f, 42f, Path.Direction.CW)
            },
        )

        if (mediaBitmaps.size == 1) {
            drawImageFit(canvas, mediaBitmaps.first().bitmap, rect)
            if (mediaBitmaps.first().isVideo) {
                drawVideoBadge(canvas, rect)
            }
        } else {
            drawMediaGrid(canvas, mediaBitmaps.take(4), rect)
        }

        canvas.restore()
        canvas.drawRoundRect(rect, 42f, 42f, strokePaint)
    }

    private fun drawMediaGrid(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
    ) {
        val rows = mediaBitmaps.chunked(2)
        val rowHeight = (rect.height() - MediaGridGap * (rows.size - 1)) / rows.size

        rows.forEachIndexed { rowIndex, row ->
            val top = rect.top + rowIndex * (rowHeight + MediaGridGap)
            val cellWidth = (rect.width() - MediaGridGap) / 2f
            row.forEachIndexed { columnIndex, media ->
                val left = rect.left + columnIndex * (cellWidth + MediaGridGap)
                val cellRect = RectF(left, top, left + cellWidth, top + rowHeight)
                drawImageFit(canvas, media.bitmap, cellRect)
                if (media.isVideo) {
                    drawVideoBadge(canvas, cellRect)
                }
            }
        }
    }

    private fun drawImageFit(
        canvas: Canvas,
        bitmap: Bitmap,
        rect: RectF,
    ) {
        val scale = min(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = rect.left + ((rect.width() - width) / 2f)
        val top = rect.top + ((rect.height() - height) / 2f)
        val destination = RectF(left, top, left + width, top + height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    private fun drawVideoBadge(
        canvas: Canvas,
        rect: RectF,
    ) {
        val badgeRadius = 44f
        val badgePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = AndroidColor.argb(170, 0, 0, 0)
            }
        val textPaint = textPaint(AndroidColor.WHITE, 29f, Typeface.SANS_SERIF, bold = true)

        canvas.drawCircle(rect.centerX(), rect.centerY(), badgeRadius, badgePaint)
        drawCenteredText(canvas, "Video", rect.centerX(), rect.centerY(), textPaint)
    }

    private fun drawLayout(
        canvas: Canvas,
        layout: StaticLayout,
        x: Float,
        y: Float,
    ) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        paint: TextPaint,
    ) {
        val baseline = centerY - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, centerX - (paint.measureText(text) / 2f), baseline, paint)
    }
}

private data class MeasuredCard(
    val dateLayout: StaticLayout,
    val contentLayout: StaticLayout,
    val relatedPost: MeasuredRelatedPost?,
    val mediaHeight: Float,
    val authorNameLayout: StaticLayout,
    val authorHandleLayout: StaticLayout,
    val markLayout: StaticLayout,
    val height: Int,
)

private data class MeasuredRelatedPost(
    val authorLayout: StaticLayout,
    val contentLayout: StaticLayout,
) {
    val height: Float
        get() = RelatedPadding + authorLayout.height + SmallGap + contentLayout.height + RelatedPadding
}

private data class RenderPalette(
    val surface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val brand: Int,
) {
    companion object {
        fun from(cardTone: CardTone): RenderPalette =
            if (cardTone == CardTone.Dark) {
                RenderPalette(
                    surface = AndroidColor.rgb(33, 26, 22),
                    textPrimary = AndroidColor.rgb(247, 232, 220),
                    textSecondary = AndroidColor.rgb(185, 170, 154),
                    brand = AndroidColor.rgb(255, 199, 168),
                )
            } else {
                RenderPalette(
                    surface = AndroidColor.rgb(255, 251, 246),
                    textPrimary = AndroidColor.rgb(33, 26, 22),
                    textSecondary = AndroidColor.rgb(117, 105, 94),
                    brand = AndroidColor.rgb(122, 68, 47),
                )
            }
    }
}

private data class ExportMediaBitmap(
    val bitmap: Bitmap,
    val isVideo: Boolean,
)

private data class ExportMediaSource(
    val url: String,
    val isVideo: Boolean,
)

private fun textPaint(
    color: Int,
    size: Float,
    typeface: Typeface,
    bold: Boolean = false,
): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
        isFakeBoldText = bold
    }

private fun textLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    maxLines: Int = Int.MAX_VALUE,
): StaticLayout =
    StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setLineSpacing(8f, 1f)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(if (maxLines == 1) TextUtils.TruncateAt.END else null)
        .build()

private fun RelatedPost.authorLabel(): String =
    "Répond à " + listOfNotNull(authorName, authorHandle)
        .joinToString("  ")
        .ifBlank { "Original post" }

private fun QuotiPost.exportMediaSources(): List<ExportMediaSource> {
    return (media + relatedPost?.media.orEmpty())
        .mapNotNull(PostMedia::exportSource)
}

private fun PostMedia.exportSource(): ExportMediaSource? {
    return when (this) {
        is PostMedia.Image -> ExportMediaSource(url = url, isVideo = false)
        is PostMedia.Video ->
            (posterUrl ?: url ?: variants.firstOrNull())
                ?.let { previewUrl -> ExportMediaSource(url = previewUrl, isVideo = true) }
    }
}

private fun withAlpha(
    color: Int,
    alpha: Float,
): Int =
    AndroidColor.argb(
        (alpha.coerceIn(0f, 1f) * 255).roundToInt(),
        AndroidColor.red(color),
        AndroidColor.green(color),
        AndroidColor.blue(color),
    )

private fun formatDate(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    return DateTimeFormatter
        .ofPattern("MMM d, yyyy", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

internal fun quotiCardFileName(
    postId: String,
    timestampMillis: Long = System.currentTimeMillis(),
): String {
    val safeId =
        postId
            .lowercase()
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(40)
            .ifBlank { "card" }

    return "quoti-$safeId-$timestampMillis.png"
}
