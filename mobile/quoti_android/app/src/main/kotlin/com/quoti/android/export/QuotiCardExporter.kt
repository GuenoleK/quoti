package com.quoti.android.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Surface
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PngMimeType = "image/png"
private const val Mp4MimeType = "video/mp4"
private const val ExportDirectory = "quoti_exports"
private const val PicturesRelativePath = "Pictures/Quoti"
private const val MoviesRelativePath = "Movies/Quoti"
private const val ExportBitmapWidth = 1080
private const val CardPadding = 72f
private const val HeaderHeight = 88f
private const val SectionGap = 48f
private const val SmallGap = 20f
private const val RelatedPadding = 34f
private const val MinMediaHeight = 260f
private const val PlaceholderMediaAspectRatio = 1.85f
private const val MediaGridGap = 6f
private const val VideoExportBitmapWidth = 720
private const val VideoExportSourceVariantMaxLongEdge = 1280
internal const val VideoExportFrameRate = 30
private const val VideoExportSourceFrameMaxLongEdge = 720
internal const val VideoExportMaxDurationMs = 60_000L
private const val VideoExportMinBitRate = 8_000_000L
private const val VideoExportMaxBitRate = 24_000_000L
private const val VideoExportBitsPerPixelFrame = 0.22
private const val VideoEncoderTimeoutUs = 10_000L
private const val VideoCodecMaxStalledPolls = 1_000

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

    suspend fun writeMoviesMp4(
        context: Context,
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
    ): Uri {
        val fileName = quotiVideoFileName(post.id)
        val output =
            withContext(Dispatchers.IO) {
                val exportDir = File(context.cacheDir, ExportDirectory)
                exportDir.mkdirs()
                File(exportDir, fileName)
            }

        renderVideoCardMp4(
            output = output,
            post = post,
            cardTone = cardTone,
            contentMode = contentMode,
        )

        return writeCacheVideoToMovies(
            context = context,
            cacheFile = output,
            fileName = fileName,
        )
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

    private suspend fun renderVideoCardMp4(
        output: File,
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
    ) {
        val mediaSources = post.exportMediaSources().take(4)
        val videoSource = mediaSources.firstOrNull { source -> source.playableVideoUrl != null }
            ?: error("No playable MP4 video source is available for this Quoti card.")
        val mediaSlots =
            mediaSources.map { source ->
                if (source === videoSource) {
                    ExportVideoMediaSlot.DynamicVideo
                } else {
                    ExportVideoMediaSlot.StaticBitmap(
                        bitmap = fetchRemoteBitmap(source.url),
                        isVideo = source.isVideo,
                    )
                }
            }

        val localVideoSource = downloadVideoSource(videoSource.playableVideoUrl ?: error("Missing playable video URL."), output)

        try {
            withContext(Dispatchers.Default) {
                renderVideoCardMp4(
                    output = output,
                    videoPath = localVideoSource.absolutePath,
                    post = post,
                    cardTone = cardTone,
                    contentMode = contentMode,
                    mediaSlots = mediaSlots,
                )
            }
        } finally {
            localVideoSource.delete()
            mediaSlots.forEach { slot ->
                if (slot is ExportVideoMediaSlot.StaticBitmap) {
                    slot.bitmap?.recycle()
                }
            }
        }
    }

    private fun renderVideoCardMp4(
        output: File,
        videoPath: String,
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
        mediaSlots: List<ExportVideoMediaSlot>,
    ) {
        var encoder: AvcBitmapEncoder? = null
        var frameRenderer: QuotiCardBitmapRenderer.VideoFrameRenderer? = null

        try {
            val sourceDurationMs = videoDurationMs(videoPath)
            val exportDurationMs = min(sourceDurationMs, VideoExportMaxDurationMs).coerceAtLeast(1_000L)
            val exportDurationUs = exportDurationMs * 1_000L
            val audioFormat = findAudioTrackFormat(videoPath)
            val frameCount = max(1, ceil(exportDurationMs / 1_000.0 * VideoExportFrameRate).toInt())
            val frameIntervalUs = 1_000_000L / VideoExportFrameRate

            decodeVideoFrames(
                videoPath = videoPath,
                frameCount = frameCount,
                frameIntervalUs = frameIntervalUs,
                maxDurationUs = exportDurationUs,
            ) { frame, presentationTimeUs ->
                val mediaBitmaps = mediaSlots.toMediaBitmaps(frame)
                val cardBitmap =
                    (frameRenderer
                        ?: QuotiCardBitmapRenderer.prepareVideoFrameRenderer(
                            post = post,
                            cardTone = cardTone,
                            contentMode = contentMode,
                            mediaBitmaps = mediaBitmaps,
                        ).also { preparedRenderer ->
                            frameRenderer = preparedRenderer
                        }).render(mediaBitmaps)

                val activeEncoder =
                    encoder
                        ?: AvcBitmapEncoder(
                            output = output,
                            width = cardBitmap.width,
                            height = cardBitmap.height,
                            frameRate = VideoExportFrameRate,
                            audioSourcePath = videoPath,
                            audioFormat = audioFormat,
                            maxDurationUs = exportDurationUs,
                        ).also { createdEncoder ->
                            encoder = createdEncoder
                        }
                activeEncoder.encode(cardBitmap, presentationTimeUs)
            }
            encoder?.finish()
        } finally {
            frameRenderer?.release()
            encoder?.release()
        }
    }

    private fun videoDurationMs(videoPath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { duration -> duration > 0L }
                ?: 1_000L
        } finally {
            retriever.release()
        }
    }

    private suspend fun downloadVideoSource(
        videoUrl: String,
        output: File,
    ): File =
        withContext(Dispatchers.IO) {
            val sourceFile = File(output.parentFile, "${output.nameWithoutExtension}-source.mp4")
            val connection = URL(videoUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 8_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", "Quoti Android")

                if (connection.responseCode !in 200..299) {
                    error("Video request failed with HTTP ${connection.responseCode}.")
                }

                connection.inputStream.use { input ->
                    sourceFile.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
                sourceFile
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun writeCacheVideoToMovies(
        context: Context,
        cacheFile: File,
        fileName: String,
    ): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, Mp4MimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, MoviesRelativePath)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

            val uri =
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create a MediaStore entry for the Quoti video.")

            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    cacheFile.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to open MediaStore output stream.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }.onFailure {
                resolver.delete(uri, null, null)
            }.getOrThrow()

            uri
        }
}

private sealed interface ExportVideoMediaSlot {
    data object DynamicVideo : ExportVideoMediaSlot

    data class StaticBitmap(
        val bitmap: Bitmap?,
        val isVideo: Boolean,
    ) : ExportVideoMediaSlot
}

private fun List<ExportVideoMediaSlot>.toMediaBitmaps(videoFrame: Bitmap): List<ExportMediaBitmap> {
    return mapNotNull { slot ->
        when (slot) {
            ExportVideoMediaSlot.DynamicVideo ->
                ExportMediaBitmap(
                    bitmap = videoFrame,
                    isVideo = false,
                )
            is ExportVideoMediaSlot.StaticBitmap ->
                slot.bitmap?.let { bitmap ->
                    ExportMediaBitmap(
                        bitmap = bitmap,
                        isVideo = slot.isVideo,
                    )
                }
        }
    }
}

private fun decodeVideoFrames(
    videoPath: String,
    frameCount: Int,
    frameIntervalUs: Long,
    maxDurationUs: Long,
    onFrame: (Bitmap, Long) -> Unit,
) {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var lastFrame: Bitmap? = null

    try {
        extractor.setDataSource(videoPath)
        val videoTrackIndex = extractor.firstTrackIndex("video/")
        if (videoTrackIndex < 0) {
            error("No video track is available in the source video.")
        }

        extractor.selectTrack(videoTrackIndex)
        val format = extractor.getTrackFormat(videoTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: error("Video track MIME type is unavailable.")
        val rotationDegrees = format.getIntegerOrDefault(MediaFormat.KEY_ROTATION, 0)
        val activeDecoder =
            MediaCodec.createDecoderByType(mime).also { createdDecoder ->
                decoder = createdDecoder
                createdDecoder.configure(format, null, null, 0)
                createdDecoder.start()
            }

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var nextFrameIndex = 0
        var stalledPolls = 0

        fun emitFramesUpTo(
            bitmap: Bitmap,
            decodedPresentationTimeUs: Long,
        ) {
            while (nextFrameIndex < frameCount && nextFrameIndex * frameIntervalUs <= decodedPresentationTimeUs) {
                val presentationTimeUs = nextFrameIndex * frameIntervalUs
                onFrame(bitmap, presentationTimeUs)
                nextFrameIndex++
                if (presentationTimeUs + frameIntervalUs > maxDurationUs) {
                    break
                }
            }
        }

        fun fillTrailingFrames() {
            val fallback = lastFrame ?: error("Unable to decode a frame from the source video.")
            while (nextFrameIndex < frameCount) {
                onFrame(fallback, nextFrameIndex * frameIntervalUs)
                nextFrameIndex++
            }
        }

        while (!outputDone && nextFrameIndex < frameCount) {
            if (!inputDone) {
                val inputBufferIndex = activeDecoder.dequeueInputBuffer(VideoEncoderTimeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = activeDecoder.getInputBuffer(inputBufferIndex)
                        ?: error("Video decoder input buffer is unavailable.")
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0L || sampleTimeUs >= maxDurationUs) {
                        activeDecoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            activeDecoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            activeDecoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                            stalledPolls = 0
                        }
                    }
                }
            }

            when (val outputBufferIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, VideoEncoderTimeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    stalledPolls++
                    check(stalledPolls <= VideoCodecMaxStalledPolls) {
                        "Timed out while decoding the source video."
                    }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    stalledPolls = 0
                }
                else -> {
                    if (outputBufferIndex >= 0) {
                        stalledPolls = 0
                        val isEndOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= nextFrameIndex * frameIntervalUs) {
                            val decodedFrame =
                                activeDecoder.getOutputImage(outputBufferIndex)?.toVideoBitmap(rotationDegrees)
                                    ?: error("Video decoder output image is unavailable.")

                            try {
                                emitFramesUpTo(decodedFrame, bufferInfo.presentationTimeUs)
                                lastFrame?.recycle()
                                lastFrame = decodedFrame.copy(Bitmap.Config.ARGB_8888, false)
                            } finally {
                                decodedFrame.recycle()
                            }
                        }

                        activeDecoder.releaseOutputBuffer(outputBufferIndex, false)
                        if (isEndOfStream) {
                            outputDone = true
                        }
                    }
                }
            }
        }

        if (nextFrameIndex < frameCount) {
            fillTrailingFrames()
        }
    } finally {
        lastFrame?.recycle()
        decoder?.runCatchingStop()
        decoder?.release()
        extractor.release()
    }
}

private fun MediaCodec.runCatchingStop() {
    runCatching { stop() }
}

private fun Image.toVideoBitmap(rotationDegrees: Int): Bitmap {
    return try {
        val crop = cropRect
        val outputWidth = crop.width()
        val outputHeight = crop.height()
        val pixels = IntArray(outputWidth * outputHeight)
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        for (row in 0 until outputHeight) {
            val imageY = crop.top + row
            for (column in 0 until outputWidth) {
                val imageX = crop.left + column
                val y = yBuffer.get(imageY * yPlane.rowStride + imageX * yPlane.pixelStride).toInt() and 0xff
                val chromaX = imageX / 2
                val chromaY = imageY / 2
                val u = uBuffer.get(chromaY * uPlane.rowStride + chromaX * uPlane.pixelStride).toInt() and 0xff
                val v = vBuffer.get(chromaY * vPlane.rowStride + chromaX * vPlane.pixelStride).toInt() and 0xff
                pixels[row * outputWidth + column] = yuvToArgb(y, u, v)
            }
        }

        Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            .scaleLongEdgeTo(VideoExportSourceFrameMaxLongEdge)
            .rotate(rotationDegrees)
    } finally {
        close()
    }
}

private fun yuvToArgb(
    y: Int,
    u: Int,
    v: Int,
): Int {
    val clampedY = (y - 16).coerceAtLeast(0)
    val shiftedU = u - 128
    val shiftedV = v - 128
    val red = ((298 * clampedY + 409 * shiftedV + 128) shr 8).coerceIn(0, 255)
    val green = ((298 * clampedY - 100 * shiftedU - 208 * shiftedV + 128) shr 8).coerceIn(0, 255)
    val blue = ((298 * clampedY + 516 * shiftedU + 128) shr 8).coerceIn(0, 255)
    return AndroidColor.rgb(red, green, blue)
}

private fun Bitmap.scaleLongEdgeTo(maxLongEdge: Int): Bitmap {
    val longEdge = max(width, height)
    if (longEdge <= maxLongEdge) {
        return this
    }

    val scale = maxLongEdge.toFloat() / longEdge.toFloat()
    val scaledWidth = max(2, (width * scale).roundToInt())
    val scaledHeight = max(2, (height * scale).roundToInt())
    val output = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    recycle()
    return output
}

private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
    val normalizedDegrees = ((rotationDegrees % 360) + 360) % 360
    if (normalizedDegrees == 0) {
        return this
    }

    val matrix = Matrix().apply { postRotate(normalizedDegrees.toFloat()) }
    val output = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    recycle()
    return output
}

private class AvcBitmapEncoder(
    output: File,
    private val width: Int,
    private val height: Int,
    frameRate: Int,
    private val audioSourcePath: String,
    private val audioFormat: MediaFormat?,
    private val maxDurationUs: Long,
) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val bufferInfo = MediaCodec.BufferInfo()
    private val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val inputSurface: EncoderInputSurface
    private var muxerStarted = false
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var released = false

    init {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate(width, height, frameRate))
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = EncoderInputSurface(codec.createInputSurface(), width, height)
        codec.start()
    }

    fun encode(
        bitmap: Bitmap,
        presentationTimeUs: Long,
    ) {
        inputSurface.drawBitmap(bitmap, presentationTimeUs)
        drain(endOfStream = false)
    }

    fun finish() {
        codec.signalEndOfInputStream()
        drain(endOfStream = true)
        copyAudioTrack()
    }

    fun release() {
        if (released) {
            return
        }

        released = true
        runCatching { inputSurface.release() }
        runCatching { codec.stop() }
        codec.release()
        runCatching { muxer.stop() }
        muxer.release()
    }

    private fun drain(endOfStream: Boolean) {
        var stalledPolls = 0
        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, VideoEncoderTimeoutUs)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) {
                        return
                    }
                    stalledPolls++
                    check(stalledPolls <= VideoCodecMaxStalledPolls) {
                        "Timed out while finishing the video encoder."
                    }
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    stalledPolls = 0
                    check(!muxerStarted) { "Video encoder output format changed twice." }
                    videoTrackIndex = muxer.addTrack(codec.outputFormat)
                    if (audioFormat != null) {
                        audioTrackIndex = muxer.addTrack(audioFormat)
                    }
                    muxer.start()
                    muxerStarted = true
                }
                outputBufferIndex >= 0 -> {
                    stalledPolls = 0
                    writeEncodedOutput(outputBufferIndex)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return
                    }
                }
            }
        }
    }

    private fun writeEncodedOutput(outputBufferIndex: Int) {
        val encodedData: ByteBuffer = codec.getOutputBuffer(outputBufferIndex)
            ?: error("Video encoder output buffer is unavailable.")

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
        }

        if (bufferInfo.size > 0) {
            check(muxerStarted) { "Video muxer has not started." }
            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
        }

        codec.releaseOutputBuffer(outputBufferIndex, false)
    }

    private fun copyAudioTrack() {
        if (audioFormat == null || audioTrackIndex < 0) {
            return
        }

        check(muxerStarted) { "Video muxer has not started." }
        copyAudioTrackToMuxer(
            sourcePath = audioSourcePath,
            muxer = muxer,
            muxerAudioTrackIndex = audioTrackIndex,
            maxDurationUs = maxDurationUs,
        )
    }
}

private class EncoderInputSurface(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
) {
    private val vertexBuffer: FloatBuffer =
        ByteBuffer
            .allocateDirect(FullFrameVertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(FullFrameVertices)
                position(0)
            }
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var positionHandle = 0
    private var textureCoordinateHandle = 0
    private var textureUniformHandle = 0
    private var released = false

    init {
        setupEgl()
        setupGl()
    }

    fun drawBitmap(
        bitmap: Bitmap,
        presentationTimeUs: Long,
    ) {
        check(!released) { "Encoder input surface has already been released." }
        checkEgl(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "Unable to make the encoder EGL context current."
        }

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glUniform1i(textureUniformHandle, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            FullFrameVertexStrideBytes,
            vertexBuffer,
        )

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle)
        GLES20.glVertexAttribPointer(
            textureCoordinateHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            FullFrameVertexStrideBytes,
            vertexBuffer,
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle)
        checkGlError("draw frame")

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeUs * 1_000L)
        checkEgl(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            "Unable to swap the encoder EGL buffers."
        }
    }

    fun release() {
        if (released) {
            return
        }

        released = true
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        surface.release()
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun setupEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get the default EGL display." }

        val version = IntArray(2)
        checkEgl(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            "Unable to initialize EGL."
        }

        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        val configAttributes =
            intArrayOf(
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE,
            )
        checkEgl(
            EGL14.eglChooseConfig(
                eglDisplay,
                configAttributes,
                0,
                configs,
                0,
                configs.size,
                configCount,
                0,
            ),
        ) {
            "Unable to choose an EGL config."
        }
        val eglConfig = configs[0] ?: error("EGL did not return a config.")

        eglContext =
            EGL14.eglCreateContext(
                eglDisplay,
                eglConfig,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
        checkEgl(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create an EGL context." }

        eglSurface =
            EGL14.eglCreateWindowSurface(
                eglDisplay,
                eglConfig,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
        checkEgl(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create an EGL window surface." }
        checkEgl(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "Unable to make the initial EGL context current."
        }
    }

    private fun setupGl() {
        program = createProgram(VertexShader, FragmentShader)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordinateHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "uTexture")
        check(positionHandle >= 0 && textureCoordinateHandle >= 0 && textureUniformHandle >= 0) {
            "Unable to resolve GL shader handles."
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("set up encoder texture")
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String,
    ): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val createdProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(createdProgram, vertexShader)
        GLES20.glAttachShader(createdProgram, fragmentShader)
        GLES20.glLinkProgram(createdProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(createdProgram)
            GLES20.glDeleteProgram(createdProgram)
            error("Unable to link encoder GL program: $info")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return createdProgram
    }

    private fun compileShader(
        shaderType: Int,
        source: String,
    ): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Unable to compile encoder GL shader: $info")
        }

        return shader
    }

    private fun checkEgl(
        condition: Boolean,
        message: () -> String,
    ) {
        val error = EGL14.eglGetError()
        check(condition && error == EGL14.EGL_SUCCESS) {
            "${message()} EGL error 0x${Integer.toHexString(error)}."
        }
    }

    private fun checkGlError(label: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) {
            "OpenGL error during $label: 0x${Integer.toHexString(error)}."
        }
    }

    private companion object {
        private const val FullFrameVertexStrideBytes = 4 * Float.SIZE_BYTES
        private val FullFrameVertices =
            floatArrayOf(
                -1f,
                -1f,
                0f,
                1f,
                1f,
                -1f,
                1f,
                1f,
                -1f,
                1f,
                0f,
                0f,
                1f,
                1f,
                1f,
                0f,
            )
        private const val VertexShader =
            """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
            """
        private const val FragmentShader =
            """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;

            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
            """
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

    fun prepareVideoFrameRenderer(
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
        mediaBitmaps: List<ExportMediaBitmap>,
    ): VideoFrameRenderer {
        val palette = RenderPalette.from(cardTone)
        val measure = measure(post, palette, contentMode, mediaBitmaps)
        val staticBase = Bitmap.createBitmap(ExportBitmapWidth, measure.height, Bitmap.Config.ARGB_8888)
        val staticCanvas = Canvas(staticBase)

        staticCanvas.drawColor(palette.surface)
        drawCard(
            canvas = staticCanvas,
            post = post,
            palette = palette,
            measure = measure,
            contentMode = contentMode,
            mediaBitmaps = mediaBitmaps,
            drawMediaContent = false,
        )

        val outputSize = videoExportSizeFor(staticBase.width, staticBase.height)
        val fullFrame = Bitmap.createBitmap(staticBase.width, staticBase.height, Bitmap.Config.ARGB_8888)
        val outputFrame =
            if (outputSize.width == fullFrame.width && outputSize.height == fullFrame.height) {
                fullFrame
            } else {
                Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
            }
        val mediaRect = mediaRectFor(measure, contentMode)

        return VideoFrameRenderer(
            staticBase = staticBase,
            fullFrame = fullFrame,
            outputFrame = outputFrame,
            drawDynamicMedia = { canvas, dynamicMediaBitmaps ->
                mediaRect?.let { rect ->
                    drawMedia(canvas, dynamicMediaBitmaps, rect, palette)
                }
            },
        )
    }

    class VideoFrameRenderer(
        private val staticBase: Bitmap,
        private val fullFrame: Bitmap,
        private val outputFrame: Bitmap,
        private val drawDynamicMedia: (Canvas, List<ExportMediaBitmap>) -> Unit,
    ) {
        private val fullCanvas = Canvas(fullFrame)
        private val outputCanvas = Canvas(outputFrame)
        private val outputRect = RectF(0f, 0f, outputFrame.width.toFloat(), outputFrame.height.toFloat())
        private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun render(mediaBitmaps: List<ExportMediaBitmap>): Bitmap {
            fullCanvas.drawBitmap(staticBase, 0f, 0f, null)
            drawDynamicMedia(fullCanvas, mediaBitmaps)

            if (outputFrame !== fullFrame) {
                outputCanvas.drawBitmap(fullFrame, null, outputRect, scalePaint)
            }

            return outputFrame
        }

        fun release() {
            staticBase.recycle()
            if (outputFrame !== fullFrame) {
                outputFrame.recycle()
            }
            fullFrame.recycle()
        }
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
        drawMediaContent: Boolean = true,
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
            if (drawMediaContent) {
                drawMedia(canvas, mediaBitmaps, mediaRect, palette)
            }
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

    private fun mediaRectFor(
        measure: MeasuredCard,
        contentMode: CardContentMode,
    ): RectF? {
        if (contentMode != CardContentMode.WithMedia || measure.mediaHeight <= 0f) {
            return null
        }

        val contentWidth = ExportBitmapWidth - CardPadding * 2
        var y = CardPadding + HeaderHeight + SectionGap + measure.contentLayout.height

        measure.relatedPost?.let { relatedPost ->
            y += SectionGap
            y += relatedPost.height
        }

        y += SectionGap
        return RectF(CardPadding, y, CardPadding + contentWidth, y + measure.mediaHeight)
    }

    private fun videoExportSizeFor(
        width: Int,
        height: Int,
    ): VideoExportSize {
        if (width <= VideoExportBitmapWidth) {
            return VideoExportSize(
                width = width.makeEven(),
                height = height.makeEven(),
            )
        }

        val scale = VideoExportBitmapWidth.toFloat() / width.toFloat()
        return VideoExportSize(
            width = VideoExportBitmapWidth.makeEven(),
            height = max(2, (height * scale).roundToInt()).makeEven(),
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

private data class VideoExportSize(
    val width: Int,
    val height: Int,
)

private data class ExportMediaSource(
    val url: String,
    val isVideo: Boolean,
    val playableVideoUrl: String? = null,
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
        is PostMedia.Video -> {
            val playableUrl = playableVideoUrl()
            (posterUrl ?: playableUrl ?: url ?: variants.firstOrNull())
                ?.let { previewUrl ->
                    ExportMediaSource(
                        url = previewUrl,
                        isVideo = true,
                        playableVideoUrl = playableUrl,
                    )
                }
        }
    }
}

private fun PostMedia.Video.playableVideoUrl(): String? {
    val candidates = listOfNotNull(url) + variants
    return selectExportVideoUrl(candidates)
}

internal fun selectExportVideoUrl(candidates: List<String>): String? {
    val directMp4Urls = candidates.distinct().filter { candidate -> candidate.isDirectMp4VideoUrl() }
    return directMp4Urls
        .filter { url -> url.videoResolution()?.longEdge()?.let { it <= VideoExportSourceVariantMaxLongEdge } ?: false }
        .maxByOrNull { url -> url.videoResolution()?.area ?: 0 }
        ?: directMp4Urls.minByOrNull { url -> url.videoResolution()?.longEdge() ?: Int.MAX_VALUE }
}

private fun String.isDirectMp4VideoUrl(): Boolean {
    return startsWith("https://") &&
        ".mp4" in this &&
        !contains("/pl/") &&
        !contains("/seg/")
}

private data class VideoResolution(
    val width: Int,
    val height: Int,
) {
    val area: Int = width * height

    fun longEdge(): Int = max(width, height)
}

private fun String.videoResolution(): VideoResolution? {
    return Regex("""/(\d+)x(\d+)/""")
        .find(this)
        ?.let { match ->
            VideoResolution(
                width = match.groupValues[1].toInt(),
                height = match.groupValues[2].toInt(),
            )
        }
}

private fun findAudioTrackFormat(sourcePath: String): MediaFormat? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(sourcePath)
        val trackIndex = extractor.firstTrackIndex("audio/")
        if (trackIndex >= 0) {
            extractor.getTrackFormat(trackIndex)
        } else {
            null
        }
    } finally {
        extractor.release()
    }
}

private fun copyAudioTrackToMuxer(
    sourcePath: String,
    muxer: MediaMuxer,
    muxerAudioTrackIndex: Int,
    maxDurationUs: Long,
) {
    val extractor = MediaExtractor()
    val bufferInfo = MediaCodec.BufferInfo()
    try {
        extractor.setDataSource(sourcePath)
        val sourceAudioTrackIndex = extractor.firstTrackIndex("audio/")
        if (sourceAudioTrackIndex < 0) {
            return
        }

        val sourceFormat = extractor.getTrackFormat(sourceAudioTrackIndex)
        val maxInputSize =
            sourceFormat
                .getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
                .coerceAtLeast(64 * 1024)
        val audioBuffer = ByteBuffer.allocate(maxInputSize)
        var firstSampleTimeUs = -1L

        extractor.selectTrack(sourceAudioTrackIndex)
        while (true) {
            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs < 0L) {
                break
            }
            if (firstSampleTimeUs < 0L) {
                firstSampleTimeUs = sampleTimeUs
            }

            val presentationTimeUs = sampleTimeUs - firstSampleTimeUs
            if (presentationTimeUs >= maxDurationUs) {
                break
            }

            audioBuffer.clear()
            val sampleSize = extractor.readSampleData(audioBuffer, 0)
            if (sampleSize <= 0) {
                break
            }

            audioBuffer.position(0)
            audioBuffer.limit(sampleSize)
            bufferInfo.set(0, sampleSize, presentationTimeUs, extractor.sampleFlags)
            muxer.writeSampleData(muxerAudioTrackIndex, audioBuffer, bufferInfo)
            extractor.advance()
        }
    } finally {
        extractor.release()
    }
}

private fun MediaExtractor.firstTrackIndex(mimePrefix: String): Int {
    for (trackIndex in 0 until trackCount) {
        val mime = getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith(mimePrefix)) {
            return trackIndex
        }
    }
    return -1
}

private fun MediaFormat.getIntegerOrDefault(
    key: String,
    defaultValue: Int,
): Int = if (containsKey(key)) getInteger(key) else defaultValue

private fun videoBitRate(
    width: Int,
    height: Int,
    frameRate: Int,
): Int {
    val pixelsPerSecond = width.toLong() * height.toLong() * frameRate.toLong()
    return (pixelsPerSecond * VideoExportBitsPerPixelFrame)
        .roundToLong()
        .coerceIn(VideoExportMinBitRate, VideoExportMaxBitRate)
        .toInt()
}

private fun Int.makeEven(): Int = if (this % 2 == 0) this else this - 1

private fun Bitmap.copyToYuv420(
    image: Image,
    pixels: IntArray,
) {
    check(this.width == image.width && this.height == image.height) {
        "Bitmap size ${this.width}x${this.height} does not match encoder size ${image.width}x${image.height}."
    }

    getPixels(pixels, 0, width, 0, 0, width, height)
    image.planes[0].buffer.fill(16.toByte())
    image.planes[1].buffer.fill(128.toByte())
    image.planes[2].buffer.fill(128.toByte())

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    for (row in 0 until height) {
        for (column in 0 until width) {
            val color = pixels[row * width + column]
            val red = (color shr 16) and 0xff
            val green = (color shr 8) and 0xff
            val blue = color and 0xff
            val y = ((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16
            val u = ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128
            val v = ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128

            yBuffer.put(row * yPlane.rowStride + column * yPlane.pixelStride, y.clampedByte())
            if (row % 2 == 0 && column % 2 == 0) {
                val chromaRow = row / 2
                val chromaColumn = column / 2
                uBuffer.put(chromaRow * uPlane.rowStride + chromaColumn * uPlane.pixelStride, u.clampedByte())
                vBuffer.put(chromaRow * vPlane.rowStride + chromaColumn * vPlane.pixelStride, v.clampedByte())
            }
        }
    }
}

private fun ByteBuffer.fill(value: Byte) {
    for (index in 0 until capacity()) {
        put(index, value)
    }
}

private fun Int.clampedByte(): Byte {
    return coerceIn(0, 255).toByte()
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

internal fun quotiVideoFileName(
    postId: String,
    timestampMillis: Long = System.currentTimeMillis(),
): String {
    val safeId =
        postId
            .lowercase()
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(40)
            .ifBlank { "card" }

    return "quoti-$safeId-$timestampMillis.mp4"
}
