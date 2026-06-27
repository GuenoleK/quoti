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
import android.graphics.SurfaceTexture
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
import android.opengl.GLES11Ext
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
import androidx.core.graphics.PathParser
import com.quoti.android.core.model.CardContentMode
import com.quoti.android.core.model.CardTone
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.SocialPlatform
import java.io.File
import java.io.OutputStream
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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
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
private const val AuthorAvatarSize = 64f
private const val RelatedAvatarSize = 48f
private const val AvatarGap = 20f
private const val MinMediaHeight = 260f
private const val TallVideoMediaMaxHeight = 1_160f
private const val MediumTallVideoMediaMaxHeight = 920f
private const val TallVideoMediaMinAspectRatio = 0.62f
private const val RelatedVideoMediaMaxHeight = 420f
private const val RelatedMediaMinHeight = 150f
private const val RelatedMediaThumbnailSize = 300f
private const val PlaceholderMediaAspectRatio = 1.85f
private const val MediaGridGap = 6f
private const val TwoMediaGridAspectRatio = 2f
private const val MultiMediaGridAspectRatio = 1.7777778f
private const val VideoExportBitmapWidth = 720
private const val VideoExportLongClipBitmapWidth = 640
private const val VideoExportSourceVariantMaxLongEdge = 720
internal const val VideoExportFrameRate = 30
internal const val VideoExportLongClipFrameRate = 30
private const val VideoExportLongClipThresholdMs = 45_000L
private const val VideoExportSourceFrameMaxLongEdge = 720
internal const val VideoExportMaxDurationMs = 180_000L
private const val VideoExportMinBitRate = 3_000_000L
private const val VideoExportMaxBitRate = 12_000_000L
private const val VideoExportBitsPerPixelFrame = 0.22
internal const val GpuVideoFrameVertexFloatCount = 24
private const val VideoEncoderTimeoutUs = 10_000L
private const val VideoCodecMaxStalledPolls = 1_000
private const val DefaultIoBufferSize = 8 * 1024
private const val ReplyRelationshipLabel = "R\u00e9pond \u00e0"
private const val XLogoPathData =
    "M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932L18.901 1.153zM17.61 20.644h2.039L6.486 3.24H4.298L17.61 20.644z"
private const val ThreadsLogoPathData =
    "M12.186,24h-0.007c-3.581,-0.024 -6.334,-1.205 -8.184,-3.509C2.35,18.44 1.5,15.586 1.472,12.01v-0.017c0.03,-3.579 0.879,-6.43 2.525,-8.482C5.845,1.205 8.6,0.024 12.18,0h0.014c2.746,0.02 5.043,0.725 6.826,2.098c1.677,1.29 2.858,3.13 3.509,5.467l-2.04,0.569c-1.104,-3.96 -3.898,-5.984 -8.304,-6.015c-2.91,0.022 -5.11,0.936 -6.54,2.717C4.307,6.504 3.616,8.914 3.589,12c0.027,3.086 0.718,5.496 2.057,7.164c1.43,1.783 3.631,2.698 6.54,2.717c2.623,-0.02 4.358,-0.631 5.8,-2.045c1.647,-1.613 1.618,-3.593 1.09,-4.798c-0.31,-0.71 -0.873,-1.3 -1.634,-1.75c-0.192,1.352 -0.622,2.446 -1.284,3.272c-0.886,1.102 -2.14,1.704 -3.73,1.79c-1.202,0.065 -2.361,-0.218 -3.259,-0.801c-1.063,-0.689 -1.685,-1.74 -1.752,-2.964c-0.065,-1.19 0.408,-2.285 1.33,-3.082c0.88,-0.76 2.119,-1.207 3.583,-1.291a13.853,13.853 0,0 1,3.02 0.142c-0.126,-0.742 -0.375,-1.332 -0.75,-1.757c-0.513,-0.586 -1.308,-0.883 -2.359,-0.89h-0.029c-0.844,0 -1.992,0.232 -2.721,1.32L7.734,7.847c0.98,-1.454 2.568,-2.256 4.478,-2.256h0.044c3.194,0.02 5.097,1.975 5.287,5.388c0.108,0.046 0.216,0.094 0.321,0.142c1.49,0.7 2.58,1.761 3.154,3.07c0.797,1.82 0.871,4.79 -1.548,7.158c-1.85,1.81 -4.094,2.628 -7.277,2.65ZM13.189,12.31c-0.242,0 -0.487,0.007 -0.739,0.021c-1.836,0.103 -2.98,0.946 -2.916,2.143c0.067,1.256 1.452,1.839 2.784,1.767c1.224,-0.065 2.818,-0.543 3.086,-3.71a10.5,10.5 0,0 0,-2.215,-0.221z"
private const val LinkedInLogoPathData =
    "M20.447,20.452h-3.554v-5.569c0,-1.328 -0.027,-3.037 -1.852,-3.037c-1.853,0 -2.136,1.445 -2.136,2.939v5.667H9.351V9h3.414v1.561h0.046c0.477,-0.9 1.637,-1.85 3.37,-1.85c3.601,0 4.267,2.37 4.267,5.455v6.286zM5.337,7.433c-1.144,0 -2.063,-0.926 -2.063,-2.065c0,-1.138 0.92,-2.063 2.063,-2.063c1.14,0 2.064,0.925 2.064,2.063c0,1.139 -0.925,2.065 -2.064,2.065zM7.119,20.452H3.555V9h3.564v11.452zM22.225,0H1.771C0.792,0 0,0.774 0,1.729v20.542C0,23.227 0.792,24 1.771,24h20.451C23.2,24 24,23.227 24,22.271V1.729C24,0.774 23.2,0 22.222,0h0.003z"
private const val FacebookLogoPathData =
    "M9.101,23.691v-7.98H6.627v-3.667h2.474v-1.58c0,-4.085 1.848,-5.978 5.858,-5.978c0.401,0 0.955,0.042 1.468,0.103a8.68,8.68 0,0 1,1.141,0.195v3.325a8.623,8.623 0,0 0,-0.653,-0.036a26.805,26.805 0,0 0,-0.733,-0.009c-0.707,0 -1.259,0.096 -1.675,0.309a1.686,1.686 0,0 0,-0.679,0.622c-0.258,0.42 -0.374,0.995 -0.374,1.752v1.297h3.919l-0.386,2.103l-0.287,1.564h-3.246v8.245C19.396,23.238 24,18.179 24,12.044c0,-6.627 -5.373,-12 -12,-12s-12,5.373 -12,12c0,5.628 3.874,10.35 9.101,11.647z"
private val VideoRequestHeaders = mapOf("User-Agent" to "Quoti Android")

internal fun platformLogoPathData(platform: SocialPlatform): String? =
    when (platform) {
        SocialPlatform.X -> XLogoPathData
        SocialPlatform.Threads -> ThreadsLogoPathData
        SocialPlatform.LinkedIn -> LinkedInLogoPathData
        SocialPlatform.Facebook -> FacebookLogoPathData
        SocialPlatform.Bluesky -> null
    }

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
        selectedVideoSourceId: String? = null,
        onProgress: (Int) -> Unit = {},
    ): Uri {
        val fileName = quotiVideoFileName(post.id)
        val output =
            withContext(Dispatchers.IO) {
                val exportDir = File(context.cacheDir, ExportDirectory)
                exportDir.mkdirs()
                File(exportDir, fileName)
            }

        return try {
            onProgress(0)
            renderVideoCardMp4(
                output = output,
                post = post,
                cardTone = cardTone,
                contentMode = contentMode,
                selectedVideoSourceId = selectedVideoSourceId,
                onProgress = onProgress,
            )
            onProgress(99)

            val uri = writeCacheVideoToMovies(
                context = context,
                cacheFile = output,
                fileName = fileName,
            )
            onProgress(100)
            uri
        } catch (cancellation: CancellationException) {
            output.delete()
            throw cancellation
        }
    }

    private suspend fun renderCardBitmap(
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
    ): Bitmap {
        val mediaBitmaps =
            if (contentMode == CardContentMode.WithMedia) {
                ExportMediaBitmaps(
                    main = fetchExportMediaBitmaps(post.media),
                    related = fetchExportMediaBitmaps(post.relatedPost?.media.orEmpty()),
                )
            } else {
                ExportMediaBitmaps.Empty
            }
        val avatarBitmaps =
            ExportAvatarBitmaps(
                author = post.authorAvatarUrl?.let { url -> fetchRemoteBitmap(url) },
                related = post.relatedPost?.authorAvatarUrl?.let { url -> fetchRemoteBitmap(url) },
            )

        return try {
            withContext(Dispatchers.Default) {
                QuotiCardBitmapRenderer.render(
                    post = post,
                    cardTone = cardTone,
                    contentMode = contentMode,
                    mediaBitmaps = mediaBitmaps,
                    avatarBitmaps = avatarBitmaps,
                )
            }
        } finally {
            avatarBitmaps.recycle()
            mediaBitmaps.recycle()
        }
    }

    private suspend fun fetchExportMediaBitmaps(media: List<PostMedia>): List<ExportMediaBitmap> {
        return media
            .exportMediaSources()
            .take(4)
            .mapNotNull { source ->
                fetchFirstRemoteBitmap(source.imageUrls)?.let { bitmap ->
                    ExportMediaBitmap(
                        bitmap = bitmap,
                        isVideo = source.isVideo,
                    )
                }
            }
    }

    private suspend fun fetchFirstRemoteBitmap(imageUrls: List<String>): Bitmap? {
        return imageUrls
            .distinct()
            .firstNotNullOfOrNull { url -> fetchRemoteBitmap(url) }
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
                    connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    connection.setRequestProperty("Referer", "https://www.threads.com/")

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
        selectedVideoSourceId: String?,
        onProgress: (Int) -> Unit,
    ) {
        val mediaSources =
            ExportMediaSources(
                main = post.media.exportMediaSources().take(4),
                related = post.relatedPost?.media.orEmpty().exportMediaSources().take(4),
            )
        val videoSource = mediaSources.videoSourceFor(selectedVideoSourceId)
            ?: error("No playable video source is available for this Quoti card.")
        val mediaSlots =
            ExportVideoMediaSlots(
                main = mediaSources.main.toVideoMediaSlots(videoSource, ::fetchRemoteBitmap),
                related = mediaSources.related.toVideoMediaSlots(videoSource, ::fetchRemoteBitmap),
            )
        val avatarBitmaps =
            ExportAvatarBitmaps(
                author = post.authorAvatarUrl?.let { url -> fetchRemoteBitmap(url) },
                related = post.relatedPost?.authorAvatarUrl?.let { url -> fetchRemoteBitmap(url) },
            )

        onProgress(2)
        val videoInputSource = prepareVideoInputSource(
            videoUrl = videoSource.playableVideoUrl ?: error("Missing playable video URL."),
            output = output,
            onProgress = { progress -> onProgress(progress.coerceIn(2, 18)) },
        )
        onProgress(20)

        try {
            withContext(Dispatchers.Default) {
                renderVideoCardMp4(
                    output = output,
                    videoSource = videoInputSource.videoSource,
                    audioSource = videoInputSource.audioSource,
                    sourceDurationMs = videoInputSource.durationMs,
                    post = post,
                    cardTone = cardTone,
                    contentMode = contentMode,
                    mediaSlots = mediaSlots,
                    avatarBitmaps = avatarBitmaps,
                    onProgress = onProgress,
                )
            }
        } finally {
            videoInputSource.localFiles.forEach { file -> file.delete() }
            avatarBitmaps.recycle()
            mediaSlots.recycle()
        }
    }

    private suspend fun renderVideoCardMp4(
        output: File,
        videoSource: String,
        audioSource: String?,
        sourceDurationMs: Long?,
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
        mediaSlots: ExportVideoMediaSlots,
        avatarBitmaps: ExportAvatarBitmaps,
        onProgress: (Int) -> Unit,
    ) {
        var encoder: AvcBitmapEncoder? = null
        var frameRenderer: QuotiCardBitmapRenderer.VideoFrameRenderer? = null
        var gpuTemplate: QuotiCardBitmapRenderer.GpuVideoFrameTemplate? = null
        var decoderOutputSurface: DecoderOutputSurface? = null
        var encodedFrameCount = 0
        var lastProgress = -1
        val exportCoroutineContext = coroutineContext
        val ensureNotCancelled = { exportCoroutineContext.ensureActive() }

        try {
            ensureNotCancelled()
            val resolvedSourceDurationMs = sourceDurationMs ?: videoDurationMs(videoSource)
            val exportDurationMs = min(resolvedSourceDurationMs, VideoExportMaxDurationMs).coerceAtLeast(1_000L)
            val exportDurationUs = exportDurationMs * 1_000L
            val exportProfile = videoExportProfileForDurationMs(exportDurationMs)
            val audioFormat = audioSource
                ?.let { source -> runCatching { findAudioTrackFormat(source) }.getOrNull() }
            val frameCount = max(1, ceil(exportDurationMs / 1_000.0 * exportProfile.frameRate).toInt())
            val frameIntervalUs = 1_000_000L / exportProfile.frameRate
            val videoTrackInfo = readVideoTrackInfo(videoSource)

            fun reportFrameProgress() {
                encodedFrameCount++
                val progress =
                    (20f + ((encodedFrameCount.toFloat() / frameCount.toFloat()) * 78f))
                        .roundToInt()
                        .coerceIn(20, 98)
                if (progress != lastProgress) {
                    onProgress(progress)
                    lastProgress = progress
                }
            }

            if (mediaSlots.supportsGpuVideoTexturePath() && videoTrackInfo.rotationDegrees == 0) {
                val videoPlaceholder = videoTrackInfo.createPlaceholderBitmap()
                try {
                    val templateMediaBitmaps =
                        ExportMediaBitmaps(
                            main = listOf(
                                ExportMediaBitmap(
                                    bitmap = videoPlaceholder,
                                    isVideo = true,
                                ),
                            ),
                        )
                    val preparedTemplate =
                        QuotiCardBitmapRenderer.prepareGpuVideoFrameTemplate(
                            post = post,
                            cardTone = cardTone,
                            contentMode = contentMode,
                            mediaBitmaps = templateMediaBitmaps,
                            avatarBitmaps = avatarBitmaps,
                            targetWidth = exportProfile.bitmapWidth,
                        )
                    gpuTemplate = preparedTemplate
                    val activeEncoder =
                        AvcBitmapEncoder(
                            output = output,
                            width = preparedTemplate.width,
                            height = preparedTemplate.height,
                            frameRate = exportProfile.frameRate,
                            audioSource = audioSource,
                            audioFormat = audioFormat,
                            maxDurationUs = exportDurationUs,
                            ensureActive = ensureNotCancelled,
                        ).also { createdEncoder ->
                            encoder = createdEncoder
                        }
                    val decoderTextureId = activeEncoder.createExternalTexture()
                    val activeDecoderOutputSurface =
                        DecoderOutputSurface(decoderTextureId).also { createdSurface ->
                            decoderOutputSurface = createdSurface
                        }

                    decodeVideoFramesToTexture(
                        videoSource = videoSource,
                        frameCount = frameCount,
                        frameIntervalUs = frameIntervalUs,
                        maxDurationUs = exportDurationUs,
                        decoderOutputSurface = activeDecoderOutputSurface,
                        videoTrackInfo = videoTrackInfo,
                        ensureActive = ensureNotCancelled,
                    ) { frame, presentationTimeUs ->
                        ensureNotCancelled()
                        activeEncoder.encodeTexture(preparedTemplate, frame, presentationTimeUs)
                        reportFrameProgress()
                    }
                    ensureNotCancelled()
                    activeEncoder.finish()
                } finally {
                    videoPlaceholder.recycle()
                }
            } else {
                decodeVideoFrames(
                    videoSource = videoSource,
                    frameCount = frameCount,
                    frameIntervalUs = frameIntervalUs,
                    maxDurationUs = exportDurationUs,
                    ensureActive = ensureNotCancelled,
                ) { frame, presentationTimeUs ->
                    ensureNotCancelled()
                    val mediaBitmaps = mediaSlots.toMediaBitmaps(frame)
                    val cardBitmap =
                        (frameRenderer
                            ?: QuotiCardBitmapRenderer.prepareVideoFrameRenderer(
                                post = post,
                                cardTone = cardTone,
                                contentMode = contentMode,
                                mediaBitmaps = mediaBitmaps,
                                avatarBitmaps = avatarBitmaps,
                                targetWidth = exportProfile.bitmapWidth,
                            ).also { preparedRenderer ->
                                frameRenderer = preparedRenderer
                            }).render(mediaBitmaps)

                    val activeEncoder =
                        encoder
                            ?: AvcBitmapEncoder(
                                output = output,
                                width = cardBitmap.width,
                                height = cardBitmap.height,
                                frameRate = exportProfile.frameRate,
                                audioSource = audioSource,
                                audioFormat = audioFormat,
                                maxDurationUs = exportDurationUs,
                                ensureActive = ensureNotCancelled,
                            ).also { createdEncoder ->
                                encoder = createdEncoder
                            }
                    activeEncoder.encode(cardBitmap, presentationTimeUs)
                    reportFrameProgress()
                }
                ensureNotCancelled()
                encoder?.finish()
            }
        } finally {
            decoderOutputSurface?.release()
            gpuTemplate?.release()
            frameRenderer?.release()
            encoder?.release()
        }
    }

    private fun videoDurationMs(videoPath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setVideoDataSource(videoPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { duration -> duration > 0L }
                ?: 1_000L
        } finally {
            retriever.release()
        }
    }

    private suspend fun prepareVideoInputSource(
        videoUrl: String,
        output: File,
        onProgress: (Int) -> Unit,
    ): VideoInputSource {
        if (videoUrl.isHlsVideoUrl()) {
            return materializeHlsVideoSource(videoUrl, output, onProgress)
        }

        onProgress(8)
        val localFile = downloadVideoSource(videoUrl, output)
        onProgress(18)
        return VideoInputSource(
            videoSource = localFile.absolutePath,
            audioSource = localFile.absolutePath,
            durationMs = null,
            localFiles = listOf(localFile),
        )
    }

    private suspend fun materializeHlsVideoSource(
        playlistUrl: String,
        output: File,
        onProgress: (Int) -> Unit,
    ): VideoInputSource =
        withContext(Dispatchers.IO) {
            onProgress(3)
            val masterPlaylist = downloadText(playlistUrl)
            val selection = selectHlsMediaPlaylistsForExport(masterPlaylist, playlistUrl)
            val videoPlaylistUrl = selection?.videoPlaylistUrl ?: playlistUrl
            val audioPlaylistUrl = selection?.audioPlaylistUrl
            val videoSourceFile = File(output.parentFile, "${output.nameWithoutExtension}-source-hls-video.mp4")
            var materializedAudioFile: File? = null

            try {
                val videoDurationMs =
                    materializeHlsMediaPlaylist(
                        playlistUrl = videoPlaylistUrl,
                        output = videoSourceFile,
                        progressStart = 4,
                        progressEnd = if (audioPlaylistUrl == null) 18 else 14,
                        onProgress = onProgress,
                    )

                materializedAudioFile =
                    audioPlaylistUrl
                        ?.let { url ->
                            val target = File(output.parentFile, "${output.nameWithoutExtension}-source-hls-audio.m4a")
                            runCatching {
                                materializeHlsMediaPlaylist(
                                    playlistUrl = url,
                                    output = target,
                                    progressStart = 15,
                                    progressEnd = 18,
                                    onProgress = onProgress,
                                )
                                target
                            }.onFailure {
                                target.delete()
                            }.getOrNull()
                        }

                VideoInputSource(
                    videoSource = videoSourceFile.absolutePath,
                    audioSource = materializedAudioFile?.absolutePath ?: videoSourceFile.absolutePath,
                    durationMs = videoDurationMs,
                    localFiles = listOfNotNull(videoSourceFile, materializedAudioFile),
                )
            } catch (throwable: Throwable) {
                videoSourceFile.delete()
                materializedAudioFile?.delete()
                throw throwable
            }
        }

    private suspend fun materializeHlsMediaPlaylist(
        playlistUrl: String,
        output: File,
        progressStart: Int,
        progressEnd: Int,
        onProgress: (Int) -> Unit,
    ): Long {
        val playlist = downloadText(playlistUrl)
        val lines = playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        var initSegmentWritten = false
        var pendingDurationSeconds = 0.0
        var totalDurationSeconds = 0.0
        val maxDurationSeconds = VideoExportMaxDurationMs / 1_000.0
        val expectedSegments = hlsExportSegmentCountForMediaPlaylist(playlist).coerceAtLeast(1)
        var downloadedSegments = 0
        var lastProgress = progressStart
        onProgress(progressStart)

        output.outputStream().buffered(DefaultIoBufferSize).use { fileOutput ->
            lines.forEach { line ->
                coroutineContext.ensureActive()
                when {
                    line.startsWith("#EXT-X-MAP:") && !initSegmentWritten -> {
                        val initUri = parseHlsAttributes(line)["URI"] ?: return@forEach
                        downloadUrlTo(
                            url = resolveHlsUrl(playlistUrl, initUri),
                            output = fileOutput,
                        )
                        initSegmentWritten = true
                    }

                    line.startsWith("#EXTINF:") -> {
                        pendingDurationSeconds =
                            line
                                .substringAfter(":")
                                .substringBefore(",")
                                .toDoubleOrNull()
                                ?: 0.0
                    }

                    line.startsWith("#") -> Unit

                    totalDurationSeconds < maxDurationSeconds -> {
                        downloadUrlTo(
                            url = resolveHlsUrl(playlistUrl, line),
                            output = fileOutput,
                        )
                        totalDurationSeconds += pendingDurationSeconds
                        pendingDurationSeconds = 0.0
                        downloadedSegments++
                        val progress =
                            (progressStart + ((downloadedSegments.toFloat() / expectedSegments.toFloat()) * (progressEnd - progressStart)))
                                .roundToInt()
                                .coerceIn(progressStart, progressEnd)
                        if (progress != lastProgress) {
                            onProgress(progress)
                            lastProgress = progress
                        }
                    }
                }
            }
        }

        check(output.length() > 0L) { "Unable to materialize HLS media playlist." }
        onProgress(progressEnd)
        return hlsExportDurationMsForMediaPlaylist(playlist)
    }

    private fun downloadText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", VideoRequestHeaders.getValue("User-Agent"))

            if (connection.responseCode !in 200..299) {
                error("Unable to fetch HLS playlist (${connection.responseCode}).")
            }

            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadUrlTo(
        url: String,
        output: OutputStream,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", VideoRequestHeaders.getValue("User-Agent"))

            if (connection.responseCode !in 200..299) {
                error("Unable to fetch HLS media segment (${connection.responseCode}).")
            }

            connection.inputStream.use { input -> input.copyTo(output, DefaultIoBufferSize) }
        } finally {
            connection.disconnect()
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
                        val buffer = ByteArray(DefaultIoBufferSize)
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) {
                                break
                            }
                            outputStream.write(buffer, 0, bytesRead)
                        }
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
                    cacheFile.inputStream().use { input ->
                        val buffer = ByteArray(DefaultIoBufferSize)
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) {
                                break
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                    }
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

private suspend fun List<ExportMediaSource>.toVideoMediaSlots(
    videoSource: ExportMediaSource,
    fetchBitmap: suspend (String) -> Bitmap?,
): List<ExportVideoMediaSlot> {
    return map { source ->
        if (source === videoSource) {
            ExportVideoMediaSlot.DynamicVideo
        } else {
            ExportVideoMediaSlot.StaticBitmap(
                bitmap = source.imageUrls.firstNotNullOfOrNull { url -> fetchBitmap(url) },
                isVideo = source.isVideo,
            )
        }
    }
}

private fun List<ExportVideoMediaSlot>.toMediaBitmaps(videoFrame: Bitmap): List<ExportMediaBitmap> {
    return mapNotNull { slot ->
        when (slot) {
            ExportVideoMediaSlot.DynamicVideo ->
                ExportMediaBitmap(
                    bitmap = videoFrame,
                    isVideo = true,
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

private fun ExportVideoMediaSlots.toMediaBitmaps(videoFrame: Bitmap): ExportMediaBitmaps {
    return ExportMediaBitmaps(
        main = main.toMediaBitmaps(videoFrame),
        related = related.toMediaBitmaps(videoFrame),
    )
}

private fun List<ExportVideoMediaSlot>.recycleVideoSlots() {
    forEach { slot ->
        if (slot is ExportVideoMediaSlot.StaticBitmap) {
            slot.bitmap?.recycle()
        }
    }
}

private fun ExportVideoMediaSlots.supportsGpuVideoTexturePath(): Boolean {
    return main.size == 1 &&
        main.first() == ExportVideoMediaSlot.DynamicVideo &&
        related.isEmpty()
}

private data class VideoTrackInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
) {
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    fun createPlaceholderBitmap(): Bitmap {
        val longEdge = max(displayWidth, displayHeight).coerceAtLeast(1)
        val scale = min(1f, VideoExportSourceFrameMaxLongEdge.toFloat() / longEdge.toFloat())
        val bitmapWidth = max(2, (displayWidth * scale).roundToInt())
        val bitmapHeight = max(2, (displayHeight * scale).roundToInt())
        return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    }
}

private fun readVideoTrackInfo(videoSource: String): VideoTrackInfo {
    val extractor = MediaExtractor()
    return try {
        extractor.setVideoDataSource(videoSource)
        val videoTrackIndex = extractor.firstTrackIndex("video/")
        if (videoTrackIndex < 0) {
            error("No video track is available in the source video.")
        }

        val format = extractor.getTrackFormat(videoTrackIndex)
        VideoTrackInfo(
            width = format.getIntegerOrDefault(MediaFormat.KEY_WIDTH, 2).coerceAtLeast(2),
            height = format.getIntegerOrDefault(MediaFormat.KEY_HEIGHT, 2).coerceAtLeast(2),
            rotationDegrees = format.getIntegerOrDefault(MediaFormat.KEY_ROTATION, 0),
        )
    } finally {
        extractor.release()
    }
}

private data class VideoTextureFrame(
    val textureId: Int,
    val textureMatrix: FloatArray,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal data class GpuVideoTextureCrop(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
)

internal fun gpuVideoTextureCoverCrop(
    containerWidth: Float,
    containerHeight: Float,
    sourceWidth: Int,
    sourceHeight: Int,
): GpuVideoTextureCrop {
    if (containerWidth <= 0f || containerHeight <= 0f || sourceWidth <= 0 || sourceHeight <= 0) {
        return GpuVideoTextureCrop(left = 0f, bottom = 0f, right = 1f, top = 1f)
    }

    val containerAspect = containerWidth / containerHeight
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()

    return if (sourceAspect > containerAspect) {
        val visibleWidth = (containerAspect / sourceAspect).coerceIn(0f, 1f)
        val horizontalInset = (1f - visibleWidth) / 2f
        GpuVideoTextureCrop(
            left = horizontalInset,
            bottom = 0f,
            right = 1f - horizontalInset,
            top = 1f,
        )
    } else {
        val visibleHeight = (sourceAspect / containerAspect).coerceIn(0f, 1f)
        val verticalInset = (1f - visibleHeight) / 2f
        GpuVideoTextureCrop(
            left = 0f,
            bottom = verticalInset,
            right = 1f,
            top = 1f - verticalInset,
        )
    }
}

internal fun populateGpuVideoFrameVertices(
    vertices: FloatArray,
    rectLeft: Float,
    rectTop: Float,
    rectRight: Float,
    rectBottom: Float,
    surfaceWidth: Int,
    surfaceHeight: Int,
    textureLeft: Float = 0f,
    textureBottom: Float = 0f,
    textureRight: Float = 1f,
    textureTop: Float = 1f,
) {
    require(vertices.size >= GpuVideoFrameVertexFloatCount) {
        "GPU video frame vertices must contain at least $GpuVideoFrameVertexFloatCount floats."
    }

    val surfaceWidthFloat = surfaceWidth.toFloat().coerceAtLeast(1f)
    val surfaceHeightFloat = surfaceHeight.toFloat().coerceAtLeast(1f)
    val left = (rectLeft / surfaceWidthFloat) * 2f - 1f
    val right = (rectRight / surfaceWidthFloat) * 2f - 1f
    val top = 1f - (rectTop / surfaceHeightFloat) * 2f
    val bottom = 1f - (rectBottom / surfaceHeightFloat) * 2f

    vertices[0] = left
    vertices[1] = bottom
    vertices[2] = textureLeft
    vertices[3] = textureBottom
    vertices[4] = 0f
    vertices[5] = 1f
    vertices[6] = right
    vertices[7] = bottom
    vertices[8] = textureRight
    vertices[9] = textureBottom
    vertices[10] = 1f
    vertices[11] = 1f
    vertices[12] = left
    vertices[13] = top
    vertices[14] = textureLeft
    vertices[15] = textureTop
    vertices[16] = 0f
    vertices[17] = 0f
    vertices[18] = right
    vertices[19] = top
    vertices[20] = textureRight
    vertices[21] = textureTop
    vertices[22] = 1f
    vertices[23] = 0f
}

private fun decodeVideoFramesToTexture(
    videoSource: String,
    frameCount: Int,
    frameIntervalUs: Long,
    maxDurationUs: Long,
    decoderOutputSurface: DecoderOutputSurface,
    videoTrackInfo: VideoTrackInfo,
    ensureActive: () -> Unit = {},
    onFrame: (VideoTextureFrame, Long) -> Unit,
) {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var lastFrame: VideoTextureFrame? = null

    try {
        extractor.setVideoDataSource(videoSource)
        val videoTrackIndex = extractor.firstTrackIndex("video/")
        if (videoTrackIndex < 0) {
            error("No video track is available in the source video.")
        }

        extractor.selectTrack(videoTrackIndex)
        val format = extractor.getTrackFormat(videoTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: error("Video track MIME type is unavailable.")
        val activeDecoder =
            MediaCodec.createDecoderByType(mime).also { createdDecoder ->
                decoder = createdDecoder
                createdDecoder.configure(format, decoderOutputSurface.surface, null, 0)
                createdDecoder.start()
            }

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var nextFrameIndex = 0
        var stalledPolls = 0

        fun emitFramesUpTo(
            frame: VideoTextureFrame,
            decodedPresentationTimeUs: Long,
        ) {
            while (nextFrameIndex < frameCount && nextFrameIndex * frameIntervalUs <= decodedPresentationTimeUs) {
                ensureActive()
                val presentationTimeUs = nextFrameIndex * frameIntervalUs
                onFrame(frame, presentationTimeUs)
                nextFrameIndex++
                if (presentationTimeUs + frameIntervalUs > maxDurationUs) {
                    break
                }
            }
        }

        fun fillTrailingFrames() {
            val fallback = lastFrame ?: error("Unable to decode a frame from the source video.")
            while (nextFrameIndex < frameCount) {
                ensureActive()
                onFrame(fallback, nextFrameIndex * frameIntervalUs)
                nextFrameIndex++
            }
        }

        while (!outputDone && nextFrameIndex < frameCount) {
            ensureActive()
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
                        val shouldRender =
                            bufferInfo.size > 0 &&
                                bufferInfo.presentationTimeUs >= nextFrameIndex * frameIntervalUs
                        activeDecoder.releaseOutputBuffer(outputBufferIndex, shouldRender)

                        if (shouldRender) {
                            decoderOutputSurface.awaitNewImage(ensureActive)
                            val frame =
                                VideoTextureFrame(
                                    textureId = decoderOutputSurface.textureId,
                                    textureMatrix = decoderOutputSurface.textureMatrix.copyOf(),
                                    sourceWidth = videoTrackInfo.displayWidth,
                                    sourceHeight = videoTrackInfo.displayHeight,
                                )
                            emitFramesUpTo(frame, bufferInfo.presentationTimeUs)
                            lastFrame = frame
                        }

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
        decoder?.runCatchingStop()
        decoder?.release()
        extractor.release()
    }
}

private fun decodeVideoFrames(
    videoSource: String,
    frameCount: Int,
    frameIntervalUs: Long,
    maxDurationUs: Long,
    ensureActive: () -> Unit = {},
    onFrame: (Bitmap, Long) -> Unit,
) {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var lastFrame: Bitmap? = null
    val frameConverter = VideoFrameBitmapConverter()

    try {
        extractor.setVideoDataSource(videoSource)
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
                ensureActive()
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
                ensureActive()
                onFrame(fallback, nextFrameIndex * frameIntervalUs)
                nextFrameIndex++
            }
        }

        while (!outputDone && nextFrameIndex < frameCount) {
            ensureActive()
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
                            val outputImage = activeDecoder.getOutputImage(outputBufferIndex)
                                ?: error("Video decoder output image is unavailable.")
                            val decodedFrame = frameConverter.convert(outputImage, rotationDegrees, ensureActive)

                            emitFramesUpTo(decodedFrame, bufferInfo.presentationTimeUs)
                            lastFrame?.recycle()
                            lastFrame = decodedFrame.copy(Bitmap.Config.ARGB_8888, false)
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
        frameConverter.release()
        decoder?.runCatchingStop()
        decoder?.release()
        extractor.release()
    }
}

private fun MediaCodec.runCatchingStop() {
    runCatching { stop() }
}

private class DecoderOutputSurface(
    val textureId: Int,
) : SurfaceTexture.OnFrameAvailableListener {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val frameSyncObject = Object()
    private val surfaceTexture = SurfaceTexture(textureId)
    val surface = Surface(surfaceTexture)
    val textureMatrix = FloatArray(16)
    private var frameAvailable = false
    private var released = false

    init {
        surfaceTexture.setOnFrameAvailableListener(this)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        synchronized(frameSyncObject) {
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    fun awaitNewImage(ensureActive: () -> Unit = {}) {
        val timeoutMs = 2_500L
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(frameSyncObject) {
            while (!frameAvailable) {
                ensureActive()
                val remainingMs = deadline - System.currentTimeMillis()
                check(remainingMs > 0L) { "Timed out waiting for a decoded video frame." }
                frameSyncObject.wait(min(25L, remainingMs))
            }
            frameAvailable = false
        }

        ensureActive()
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(textureMatrix)
    }

    fun release() {
        if (released) {
            return
        }

        released = true
        surface.release()
        surfaceTexture.release()
    }
}

private class VideoFrameBitmapConverter {
    private var pixelBuffer = IntArray(0)
    private var rawBitmap: Bitmap? = null
    private var scaledBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private val transformPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun convert(
        image: Image,
        rotationDegrees: Int,
        ensureActive: () -> Unit = {},
    ): Bitmap {
        try {
            val rawFrame = image.toReusableRawBitmap(ensureActive)
            val scaledFrame = rawFrame.scaledLongEdgeToReusable(VideoExportSourceFrameMaxLongEdge)
            return scaledFrame.rotatedToReusable(rotationDegrees)
        } finally {
            image.close()
        }
    }

    fun release() {
        rawBitmap?.recycle()
        scaledBitmap?.recycle()
        rotatedBitmap?.recycle()
        rawBitmap = null
        scaledBitmap = null
        rotatedBitmap = null
    }

    private fun Image.toReusableRawBitmap(ensureActive: () -> Unit): Bitmap {
        val crop = cropRect
        val outputWidth = crop.width()
        val outputHeight = crop.height()
        val pixelCount = outputWidth * outputHeight
        if (pixelBuffer.size < pixelCount) {
            pixelBuffer = IntArray(pixelCount)
        }
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        for (row in 0 until outputHeight) {
            if (row % 16 == 0) {
                ensureActive()
            }
            val imageY = crop.top + row
            for (column in 0 until outputWidth) {
                val imageX = crop.left + column
                val y = yBuffer.get(imageY * yPlane.rowStride + imageX * yPlane.pixelStride).toInt() and 0xff
                val chromaX = imageX / 2
                val chromaY = imageY / 2
                val u = uBuffer.get(chromaY * uPlane.rowStride + chromaX * uPlane.pixelStride).toInt() and 0xff
                val v = vBuffer.get(chromaY * vPlane.rowStride + chromaX * vPlane.pixelStride).toInt() and 0xff
                pixelBuffer[row * outputWidth + column] = yuvToArgb(y, u, v)
            }
        }

        val bitmap = rawBitmap.reuseOrCreate(outputWidth, outputHeight).also { reusable ->
            rawBitmap = reusable
        }
        bitmap.setPixels(pixelBuffer, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        return bitmap
    }

    private fun Bitmap.scaledLongEdgeToReusable(maxLongEdge: Int): Bitmap {
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) {
            return this
        }

        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val scaledWidth = max(2, (width * scale).roundToInt())
        val scaledHeight = max(2, (height * scale).roundToInt())
        val output = scaledBitmap.reuseOrCreate(scaledWidth, scaledHeight).also { reusable ->
            scaledBitmap = reusable
        }
        Canvas(output).drawBitmap(this, null, RectF(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat()), transformPaint)
        return output
    }

    private fun Bitmap.rotatedToReusable(rotationDegrees: Int): Bitmap {
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> drawRotatedToReusable(width = height, height = width, degrees = 90f) { canvas ->
                canvas.translate(height.toFloat(), 0f)
            }
            180 -> drawRotatedToReusable(width = width, height = height, degrees = 180f) { canvas ->
                canvas.translate(width.toFloat(), height.toFloat())
            }
            270 -> drawRotatedToReusable(width = height, height = width, degrees = 270f) { canvas ->
                canvas.translate(0f, width.toFloat())
            }
            else -> this
        }
    }

    private fun Bitmap.drawRotatedToReusable(
        width: Int,
        height: Int,
        degrees: Float,
        translate: (Canvas) -> Unit,
    ): Bitmap {
        val output = rotatedBitmap.reuseOrCreate(width, height).also { reusable ->
            rotatedBitmap = reusable
        }
        output.eraseColor(AndroidColor.TRANSPARENT)
        Canvas(output).apply {
            save()
            translate(this)
            rotate(degrees)
            drawBitmap(this@drawRotatedToReusable, 0f, 0f, transformPaint)
            restore()
        }
        return output
    }
}

private fun Bitmap?.reuseOrCreate(
    width: Int,
    height: Int,
): Bitmap {
    if (this != null && !isRecycled && this.width == width && this.height == height) {
        return this
    }

    this?.recycle()
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

private class AvcBitmapEncoder(
    output: File,
    private val width: Int,
    private val height: Int,
    frameRate: Int,
    private val audioSource: String?,
    private val audioFormat: MediaFormat?,
    private val maxDurationUs: Long,
    private val ensureActive: () -> Unit = {},
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
        ensureActive()
        inputSurface.drawBitmap(bitmap, presentationTimeUs)
        drain(endOfStream = false)
    }

    fun createExternalTexture(): Int {
        ensureActive()
        return inputSurface.createExternalTexture()
    }

    fun encodeTexture(
        template: QuotiCardBitmapRenderer.GpuVideoFrameTemplate,
        frame: VideoTextureFrame,
        presentationTimeUs: Long,
    ) {
        ensureActive()
        inputSurface.drawTextureFrame(template, frame, presentationTimeUs)
        drain(endOfStream = false)
    }

    fun finish() {
        ensureActive()
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
            ensureActive()
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
        if (audioSource == null || audioFormat == null || audioTrackIndex < 0) {
            return
        }

        check(muxerStarted) { "Video muxer has not started." }
        copyAudioTrackToMuxer(
            sourcePath = audioSource,
            muxer = muxer,
            muxerAudioTrackIndex = audioTrackIndex,
            maxDurationUs = maxDurationUs,
            ensureActive = ensureActive,
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
    private val videoVertexBuffer: FloatBuffer =
        ByteBuffer
            .allocateDirect(VideoFrameVertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    private val videoFrameVertices = FloatArray(VideoFrameVertices.size)
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var externalProgram = 0
    private var textureId = 0
    private var templateTextureId = 0
    private var templateOverlayTextureId = 0
    private var positionHandle = 0
    private var textureCoordinateHandle = 0
    private var textureUniformHandle = 0
    private var externalPositionHandle = 0
    private var externalTextureCoordinateHandle = 0
    private var externalQuadCoordinateHandle = 0
    private var externalTextureUniformHandle = 0
    private var externalTextureMatrixHandle = 0
    private var externalRectSizeHandle = 0
    private var externalCornerRadiusHandle = 0
    private var textureWidth = 0
    private var textureHeight = 0
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
        if (textureWidth == bitmap.width && textureHeight == bitmap.height) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureWidth = bitmap.width
            textureHeight = bitmap.height
        }
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

    fun createExternalTexture(): Int {
        check(!released) { "Encoder input surface has already been released." }
        checkEgl(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "Unable to make the encoder EGL context current."
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val externalTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("create decoder texture")
        return externalTextureId
    }

    fun drawTextureFrame(
        template: QuotiCardBitmapRenderer.GpuVideoFrameTemplate,
        frame: VideoTextureFrame,
        presentationTimeUs: Long,
    ) {
        check(!released) { "Encoder input surface has already been released." }
        checkEgl(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "Unable to make the encoder EGL context current."
        }

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        drawTexture2d(
            textureId = templateTexture(template.staticBase, isOverlay = false),
            blend = false,
        )
        drawExternalVideoTexture(template, frame)
        drawTexture2d(
            textureId = templateTexture(template.overlay, isOverlay = true),
            blend = true,
        )
        checkGlError("draw texture frame")

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeUs * 1_000L)
        checkEgl(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            "Unable to swap the encoder EGL buffers."
        }
    }

    private fun templateTexture(
        bitmap: Bitmap,
        isOverlay: Boolean,
    ): Int {
        val currentTextureId = if (isOverlay) templateOverlayTextureId else templateTextureId
        if (currentTextureId != 0) {
            return currentTextureId
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val createdTextureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, createdTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        checkGlError("upload template texture")

        if (isOverlay) {
            templateOverlayTextureId = createdTextureId
        } else {
            templateTextureId = createdTextureId
        }
        return createdTextureId
    }

    private fun drawTexture2d(
        textureId: Int,
        blend: Boolean,
    ) {
        if (blend) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
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

        if (blend) {
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun drawExternalVideoTexture(
        template: QuotiCardBitmapRenderer.GpuVideoFrameTemplate,
        frame: VideoTextureFrame,
    ) {
        val textureCrop = gpuVideoTextureCoverCrop(
            containerWidth = template.videoRect.width(),
            containerHeight = template.videoRect.height(),
            sourceWidth = frame.sourceWidth,
            sourceHeight = frame.sourceHeight,
        )
        putVideoVertices(template.videoRect, textureCrop)

        GLES20.glUseProgram(externalProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frame.textureId)
        GLES20.glUniform1i(externalTextureUniformHandle, 0)
        GLES20.glUniformMatrix4fv(externalTextureMatrixHandle, 1, false, frame.textureMatrix, 0)
        GLES20.glUniform2f(externalRectSizeHandle, template.videoRect.width(), template.videoRect.height())
        GLES20.glUniform1f(externalCornerRadiusHandle, template.videoCornerRadius)

        videoVertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(externalPositionHandle)
        GLES20.glVertexAttribPointer(
            externalPositionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            VideoFrameVertexStrideBytes,
            videoVertexBuffer,
        )

        videoVertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(externalTextureCoordinateHandle)
        GLES20.glVertexAttribPointer(
            externalTextureCoordinateHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            VideoFrameVertexStrideBytes,
            videoVertexBuffer,
        )

        videoVertexBuffer.position(4)
        GLES20.glEnableVertexAttribArray(externalQuadCoordinateHandle)
        GLES20.glVertexAttribPointer(
            externalQuadCoordinateHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            VideoFrameVertexStrideBytes,
            videoVertexBuffer,
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(externalPositionHandle)
        GLES20.glDisableVertexAttribArray(externalTextureCoordinateHandle)
        GLES20.glDisableVertexAttribArray(externalQuadCoordinateHandle)
    }

    private fun putVideoVertices(
        rect: RectF,
        textureCrop: GpuVideoTextureCrop,
    ) {
        populateGpuVideoFrameVertices(
            vertices = videoFrameVertices,
            rectLeft = rect.left,
            rectTop = rect.top,
            rectRight = rect.right,
            rectBottom = rect.bottom,
            surfaceWidth = width,
            surfaceHeight = height,
            textureLeft = textureCrop.left,
            textureBottom = textureCrop.bottom,
            textureRight = textureCrop.right,
            textureTop = textureCrop.top,
        )
        videoVertexBuffer.clear()
        videoVertexBuffer.put(videoFrameVertices)
        videoVertexBuffer.position(0)
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
        externalProgram = createProgram(ExternalVertexShader, ExternalFragmentShader)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordinateHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "uTexture")
        check(positionHandle >= 0 && textureCoordinateHandle >= 0 && textureUniformHandle >= 0) {
            "Unable to resolve GL shader handles."
        }
        externalPositionHandle = GLES20.glGetAttribLocation(externalProgram, "aPosition")
        externalTextureCoordinateHandle = GLES20.glGetAttribLocation(externalProgram, "aTexCoord")
        externalQuadCoordinateHandle = GLES20.glGetAttribLocation(externalProgram, "aQuadCoord")
        externalTextureUniformHandle = GLES20.glGetUniformLocation(externalProgram, "uTexture")
        externalTextureMatrixHandle = GLES20.glGetUniformLocation(externalProgram, "uTextureMatrix")
        externalRectSizeHandle = GLES20.glGetUniformLocation(externalProgram, "uRectSize")
        externalCornerRadiusHandle = GLES20.glGetUniformLocation(externalProgram, "uCornerRadius")
        check(
            externalPositionHandle >= 0 &&
                externalTextureCoordinateHandle >= 0 &&
                externalQuadCoordinateHandle >= 0 &&
                externalTextureUniformHandle >= 0 &&
                externalTextureMatrixHandle >= 0 &&
                externalRectSizeHandle >= 0 &&
                externalCornerRadiusHandle >= 0,
        ) {
            "Unable to resolve external video GL shader handles."
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
        private const val VideoFrameVertexStrideBytes = 6 * Float.SIZE_BYTES
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
        private val VideoFrameVertices = FloatArray(GpuVideoFrameVertexFloatCount)
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
        private const val ExternalVertexShader =
            """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            attribute vec2 aQuadCoord;
            uniform mat4 uTextureMatrix;
            varying vec2 vTexCoord;
            varying vec2 vQuadCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTextureMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
                vQuadCoord = aQuadCoord;
            }
            """
        private const val ExternalFragmentShader =
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            varying vec2 vQuadCoord;
            uniform samplerExternalOES uTexture;
            uniform vec2 uRectSize;
            uniform float uCornerRadius;

            void main() {
                if (uCornerRadius > 0.0) {
                    vec2 point = vQuadCoord * uRectSize;
                    vec2 nearestEdge = min(point, uRectSize - point);
                    if (nearestEdge.x < uCornerRadius && nearestEdge.y < uCornerRadius) {
                        vec2 delta = vec2(uCornerRadius) - nearestEdge;
                        if (dot(delta, delta) > uCornerRadius * uCornerRadius) {
                            discard;
                        }
                    }
                }
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
        mediaBitmaps: ExportMediaBitmaps,
        avatarBitmaps: ExportAvatarBitmaps,
    ): Bitmap {
        val palette = RenderPalette.from(cardTone)
        val measure = measure(post, palette, contentMode, mediaBitmaps, avatarBitmaps)
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
        mediaBitmaps: ExportMediaBitmaps,
        avatarBitmaps: ExportAvatarBitmaps,
        targetWidth: Int,
    ): VideoFrameRenderer {
        val palette = RenderPalette.from(cardTone)
        val measure = measure(post, palette, contentMode, mediaBitmaps, avatarBitmaps)
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

        val outputSize = videoExportSizeFor(staticBase.width, staticBase.height, targetWidth)
        val outputScale = outputSize.width.toFloat() / staticBase.width.toFloat()
        val staticFrameBase =
            if (outputSize.width == staticBase.width && outputSize.height == staticBase.height) {
                staticBase
            } else {
                Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888).also { scaledBase ->
                    Canvas(scaledBase).drawBitmap(
                        staticBase,
                        null,
                        RectF(0f, 0f, outputSize.width.toFloat(), outputSize.height.toFloat()),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                    staticBase.recycle()
                }
            }
        val outputFrame = Bitmap.createBitmap(staticFrameBase.width, staticFrameBase.height, Bitmap.Config.ARGB_8888)
        val mediaRects = mediaRectsFor(measure, contentMode)

        return VideoFrameRenderer(
            staticBase = staticFrameBase,
            outputFrame = outputFrame,
            outputScale = outputScale,
            drawDynamicMedia = { canvas, dynamicMediaBitmaps ->
                mediaRects.main?.let { rect ->
                    drawMedia(
                        canvas = canvas,
                        mediaBitmaps = dynamicMediaBitmaps.main,
                        rect = rect,
                        palette = palette,
                        cropSingle = dynamicMediaBitmaps.main.shouldCoverSingleVideo(),
                    )
                }
                mediaRects.related?.let { rect ->
                    drawMedia(canvas, dynamicMediaBitmaps.related, rect, palette, cropSingle = true)
                }
            },
        )
    }

    fun prepareGpuVideoFrameTemplate(
        post: QuotiPost,
        cardTone: CardTone,
        contentMode: CardContentMode,
        mediaBitmaps: ExportMediaBitmaps,
        avatarBitmaps: ExportAvatarBitmaps,
        targetWidth: Int,
    ): GpuVideoFrameTemplate {
        val palette = RenderPalette.from(cardTone)
        val measure = measure(post, palette, contentMode, mediaBitmaps, avatarBitmaps)
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

        val mediaRect = mediaRectsFor(measure, contentMode).main
            ?: error("The GPU video renderer requires a main media rect.")
        drawMediaFrameFill(staticCanvas, mediaRect, palette)

        val outputSize = videoExportSizeFor(staticBase.width, staticBase.height, targetWidth)
        val outputScale = outputSize.width.toFloat() / staticBase.width.toFloat()
        val scaledBase = staticBase.scaledTo(outputSize)
        if (scaledBase !== staticBase) {
            staticBase.recycle()
        }

        val overlay = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val overlayCanvas = Canvas(overlay)
        overlayCanvas.save()
        overlayCanvas.scale(outputScale, outputScale)
        drawMediaFrameStroke(overlayCanvas, mediaRect, palette)
        overlayCanvas.restore()

        return GpuVideoFrameTemplate(
            staticBase = scaledBase,
            overlay = overlay,
            videoRect = mediaRect.scaledBy(outputScale),
            videoCornerRadius = 42f * outputScale,
        )
    }

    class VideoFrameRenderer(
        private val staticBase: Bitmap,
        private val outputFrame: Bitmap,
        private val outputScale: Float,
        private val drawDynamicMedia: (Canvas, ExportMediaBitmaps) -> Unit,
    ) {
        private val outputCanvas = Canvas(outputFrame)

        fun render(mediaBitmaps: ExportMediaBitmaps): Bitmap {
            outputCanvas.drawBitmap(staticBase, 0f, 0f, null)
            outputCanvas.save()
            if (outputScale != 1f) {
                outputCanvas.scale(outputScale, outputScale)
            }
            drawDynamicMedia(outputCanvas, mediaBitmaps)
            outputCanvas.restore()

            return outputFrame
        }

        fun release() {
            staticBase.recycle()
            outputFrame.recycle()
        }
    }

    class GpuVideoFrameTemplate(
        val staticBase: Bitmap,
        val overlay: Bitmap,
        val videoRect: RectF,
        val videoCornerRadius: Float,
    ) {
        val width: Int = staticBase.width
        val height: Int = staticBase.height

        fun release() {
            staticBase.recycle()
            overlay.recycle()
        }
    }

    private fun measure(
        post: QuotiPost,
        palette: RenderPalette,
        contentMode: CardContentMode,
        mediaBitmaps: ExportMediaBitmaps,
        avatarBitmaps: ExportAvatarBitmaps,
    ): MeasuredCard {
        val contentWidth = ExportBitmapWidth - (CardPadding * 2).roundToInt()
        val authorAvatarSlot = if (avatarBitmaps.author != null) AuthorAvatarSize + AvatarGap else 0f
        val authorWidth = ((contentWidth * 0.56f) - authorAvatarSlot).roundToInt().coerceAtLeast(260)
        val contentPaint = textPaint(palette.textPrimary, 50f, Typeface.SERIF)
        val metadataPaint = textPaint(palette.textSecondary, 32f, Typeface.SANS_SERIF)
        val authorPaint = textPaint(palette.textPrimary, 34f, Typeface.SANS_SERIF, bold = true)
        val handlePaint = textPaint(palette.textSecondary, 31f, Typeface.SANS_SERIF)
        val brandPaint = textPaint(palette.brand, 40f, Typeface.SERIF, bold = true)
        val authorNameText = post.authorName.displayTextOrNull().orEmpty()
        val authorHandleText = post.authorHandle.displayTextOrNull().orEmpty()

        val related = post.relatedPost?.let { relatedPost ->
            val relatedAvatarSlot = if (avatarBitmaps.related != null) RelatedAvatarSize + AvatarGap else 0f
            val relatedContentWidth = (contentWidth - RelatedPadding * 2).roundToInt()
            val hasRelatedMedia = contentMode == CardContentMode.WithMedia && relatedPost.media.isNotEmpty()
            val relatedMediaHeight =
                if (hasRelatedMedia) {
                    measureRelatedMediaHeight(relatedContentWidth.toFloat(), mediaBitmaps.related)
                } else {
                    0f
                }
            val relatedAuthorWidth =
                (relatedContentWidth - relatedAvatarSlot).roundToInt().coerceAtLeast(240)
            MeasuredRelatedPost(
                authorAvatar = avatarBitmaps.related,
                authorLayout =
                    textLayout(
                        text = relatedPost.authorLabel(),
                        paint = metadataPaint,
                        width = relatedAuthorWidth,
                        maxLines = 1,
                    ),
                contentLayout =
                    textLayout(
                        text = relatedPost.content,
                        paint = textPaint(palette.textPrimary, 32f, Typeface.SERIF),
                        width = relatedContentWidth,
                        maxLines = if (hasRelatedMedia) 4 else Int.MAX_VALUE,
                    ),
                mediaHeight = relatedMediaHeight,
            )
        }
        val mediaFrame =
            if (contentMode == CardContentMode.WithMedia && post.media.isNotEmpty()) {
                measureMediaFrame(contentWidth.toFloat(), mediaBitmaps.main)
            } else {
                ExportMediaFrameSize(width = 0f, height = 0f)
            }
        val authorNameLayout =
            textLayout(
                text = authorNameText,
                paint = authorPaint,
                width = authorWidth,
                maxLines = 1,
            )
        val authorHandleLayout =
            textLayout(
                text = authorHandleText,
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
        val footerHeight = 72f

        var height = CardPadding + HeaderHeight + SectionGap
        height += textLayout(post.content, contentPaint, contentWidth).height
        if (mediaFrame.height > 0f) {
            height += SectionGap
            height += mediaFrame.height
        }
        related?.let {
            height += SectionGap
            height += it.height
        }
        height += SectionGap
        height += 2f
        height += SmallGap
        height += footerHeight
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
            mediaWidth = mediaFrame.width,
            mediaHeight = mediaFrame.height,
            authorAvatar = avatarBitmaps.author,
            authorNameLayout = authorNameLayout,
            authorNameVisible = authorNameText.isNotEmpty(),
            authorHandleLayout = authorHandleLayout,
            authorHandleVisible = authorHandleText.isNotEmpty(),
            markLayout = markLayout,
            footerHeight = footerHeight,
            height = ceil(height).toInt(),
        )
    }

    private fun drawCard(
        canvas: Canvas,
        post: QuotiPost,
        palette: RenderPalette,
        measure: MeasuredCard,
        contentMode: CardContentMode,
        mediaBitmaps: ExportMediaBitmaps,
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

        val authorNameHeight =
            if (measure.authorNameVisible) {
                measure.authorNameLayout.height.toFloat()
            } else {
                0f
            }
        val authorHandleHeight =
            if (measure.authorHandleVisible) {
                measure.authorHandleLayout.height.toFloat()
            } else {
                0f
            }
        val authorLineGap =
            if (measure.authorNameVisible && measure.authorHandleVisible) {
                10f
            } else {
                0f
            }
        val authorStackHeight = authorNameHeight + authorLineGap + authorHandleHeight
        val authorTextY =
            if (authorStackHeight > 0f) {
                y + ((HeaderHeight - authorStackHeight) / 2f)
            } else {
                y
            }
        val authorTextX =
            if (measure.authorAvatar != null) {
                CardPadding + AuthorAvatarSize + AvatarGap
            } else {
                CardPadding
            }
        measure.authorAvatar?.let { avatar ->
            val avatarTop = y + ((HeaderHeight - AuthorAvatarSize) / 2f)
            drawCircularImage(
                canvas = canvas,
                bitmap = avatar,
                rect = RectF(CardPadding, avatarTop, CardPadding + AuthorAvatarSize, avatarTop + AuthorAvatarSize),
            )
        }
        var authorLineY = authorTextY
        if (measure.authorNameVisible) {
            drawLayout(canvas, measure.authorNameLayout, authorTextX, authorLineY)
            authorLineY += measure.authorNameLayout.height.toFloat()
        }
        if (measure.authorHandleVisible) {
            if (measure.authorNameVisible) {
                authorLineY += 10f
            }
            drawLayout(
                canvas = canvas,
                layout = measure.authorHandleLayout,
                x = authorTextX,
                y = authorLineY,
            )
        }
        drawLayout(
            canvas = canvas,
            layout = measure.dateLayout,
            x = ExportBitmapWidth - CardPadding - measure.dateLayout.width,
            y = authorTextY,
        )

        y += HeaderHeight + SectionGap
        drawLayout(canvas, measure.contentLayout, CardPadding, y)
        y += measure.contentLayout.height

        if (contentMode == CardContentMode.WithMedia && measure.mediaHeight > 0f) {
            y += SectionGap
            val mediaRect = mainMediaRect(
                contentWidth = contentWidth,
                mediaWidth = measure.mediaWidth,
                mediaHeight = measure.mediaHeight,
                top = y,
            )
            if (drawMediaContent) {
                drawMedia(
                    canvas = canvas,
                    mediaBitmaps = mediaBitmaps.main,
                    rect = mediaRect,
                    palette = palette,
                    cropSingle = mediaBitmaps.main.shouldCoverSingleVideo(),
                )
            }
            y += measure.mediaHeight
        }

        measure.relatedPost?.let { relatedPost ->
            y += SectionGap
            val relatedHeight = relatedPost.height
            val relatedRect = RectF(CardPadding, y, CardPadding + contentWidth, y + relatedHeight)
            strokePaint.color = withAlpha(palette.textPrimary, 0.14f)
            canvas.drawRoundRect(relatedRect, 34f, 34f, strokePaint)
            var relatedY = y + RelatedPadding
            val relatedX = CardPadding + RelatedPadding
            relatedPost.authorAvatar?.let { avatar ->
                val avatarTop = relatedY + ((relatedPost.headerHeight - RelatedAvatarSize) / 2f)
                drawCircularImage(
                    canvas = canvas,
                    bitmap = avatar,
                    rect = RectF(relatedX, avatarTop, relatedX + RelatedAvatarSize, avatarTop + RelatedAvatarSize),
                )
            }
            val relatedAuthorX =
                if (relatedPost.authorAvatar != null) {
                    relatedX + RelatedAvatarSize + AvatarGap
                } else {
                    relatedX
                }
            drawLayout(
                canvas = canvas,
                layout = relatedPost.authorLayout,
                x = relatedAuthorX,
                y = relatedY + ((relatedPost.headerHeight - relatedPost.authorLayout.height) / 2f),
            )
            relatedY += relatedPost.headerHeight + SmallGap
            drawLayout(canvas, relatedPost.contentLayout, CardPadding + RelatedPadding, relatedY)
            relatedY += relatedPost.contentLayout.height
            if (relatedPost.mediaHeight > 0f) {
                relatedY += SmallGap
                val relatedMediaRect =
                    RectF(
                        CardPadding + RelatedPadding,
                        relatedY,
                        CardPadding + RelatedPadding + (contentWidth - RelatedPadding * 2),
                        relatedY + relatedPost.mediaHeight,
                    )
                if (drawMediaContent) {
                    drawMedia(canvas, mediaBitmaps.related, relatedMediaRect, palette, cropSingle = true)
                }
            }
            y += relatedHeight
        }

        y += SectionGap
        strokePaint.color = withAlpha(palette.textPrimary, 0.14f)
        canvas.drawLine(CardPadding, y, CardPadding + contentWidth, y, strokePaint)
        y += SmallGap

        val footerTop = y
        val platformBadgeTop = footerTop + ((measure.footerHeight - 72f) / 2f)
        fillPaint.color = withAlpha(palette.textPrimary, 0.12f)
        canvas.drawOval(RectF(CardPadding, platformBadgeTop, CardPadding + 72f, platformBadgeTop + 72f), fillPaint)
        drawPlatformMark(
            canvas = canvas,
            platform = post.platform,
            rect = RectF(CardPadding + 20f, platformBadgeTop + 20f, CardPadding + 52f, platformBadgeTop + 52f),
            color = palette.textPrimary,
        )
        drawLayout(
            canvas = canvas,
            layout = measure.markLayout,
            x = ExportBitmapWidth - CardPadding - measure.markLayout.width,
            y = footerTop + ((measure.footerHeight - measure.markLayout.height) / 2f),
        )
    }

    private fun mediaRectsFor(
        measure: MeasuredCard,
        contentMode: CardContentMode,
    ): MediaRects {
        if (contentMode != CardContentMode.WithMedia) {
            return MediaRects()
        }

        val contentWidth = ExportBitmapWidth - CardPadding * 2
        var y = CardPadding + HeaderHeight + SectionGap + measure.contentLayout.height
        var relatedRect: RectF? = null

        val mainRect =
            if (measure.mediaHeight > 0f) {
                y += SectionGap
                mainMediaRect(
                    contentWidth = contentWidth,
                    mediaWidth = measure.mediaWidth,
                    mediaHeight = measure.mediaHeight,
                    top = y,
                )
                    .also { y += measure.mediaHeight }
            } else {
                null
            }

        measure.relatedPost?.let { relatedPost ->
            y += SectionGap
            if (relatedPost.mediaHeight > 0f) {
                val relatedMediaTop =
                    y +
                        RelatedPadding +
                        relatedPost.headerHeight +
                        SmallGap +
                        relatedPost.contentLayout.height +
                        SmallGap
                relatedRect =
                    RectF(
                        CardPadding + RelatedPadding,
                        relatedMediaTop,
                        CardPadding + RelatedPadding + (contentWidth - RelatedPadding * 2),
                        relatedMediaTop + relatedPost.mediaHeight,
                    )
            }
            y += relatedPost.height
        }

        return MediaRects(
            main = mainRect,
            related = relatedRect,
        )
    }

    private fun mainMediaRect(
        contentWidth: Float,
        mediaWidth: Float,
        mediaHeight: Float,
        top: Float,
    ): RectF {
        val resolvedMediaWidth = mediaWidth.takeIf { width -> width > 0f } ?: contentWidth
        val left = CardPadding + ((contentWidth - resolvedMediaWidth) / 2f)
        return RectF(left, top, left + resolvedMediaWidth, top + mediaHeight)
    }

    private fun videoExportSizeFor(
        width: Int,
        height: Int,
        targetWidth: Int,
    ): VideoExportSize {
        if (width <= targetWidth) {
            return VideoExportSize(
                width = width.makeEven(),
                height = height.makeEven(),
            )
        }

        val scale = targetWidth.toFloat() / width.toFloat()
        return VideoExportSize(
            width = targetWidth.makeEven(),
            height = max(2, (height * scale).roundToInt()).makeEven(),
        )
    }

    private fun Bitmap.scaledTo(size: VideoExportSize): Bitmap {
        if (width == size.width && height == size.height) {
            return this
        }

        return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { scaled ->
            Canvas(scaled).drawBitmap(
                this,
                null,
                RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun RectF.scaledBy(scale: Float): RectF =
        RectF(
            left * scale,
            top * scale,
            right * scale,
            bottom * scale,
        )

    private fun drawMediaFrameFill(
        canvas: Canvas,
        rect: RectF,
        palette: RenderPalette,
    ) {
        val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = withAlpha(palette.textPrimary, 0.08f)
            }
        canvas.drawRoundRect(rect, 42f, 42f, fillPaint)
    }

    private fun drawMediaFrameStroke(
        canvas: Canvas,
        rect: RectF,
        palette: RenderPalette,
    ) {
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = withAlpha(palette.textPrimary, 0.14f)
            }
        canvas.drawRoundRect(rect, 42f, 42f, strokePaint)
    }

    private fun measureMediaFrame(
        contentWidth: Float,
        mediaBitmaps: List<ExportMediaBitmap>,
    ): ExportMediaFrameSize {
        return mainMediaFrameSizeFor(
            contentWidth = contentWidth,
            mediaCount = mediaBitmaps.size,
            firstMediaWidth = mediaBitmaps.firstOrNull()?.bitmap?.width,
            firstMediaHeight = mediaBitmaps.firstOrNull()?.bitmap?.height,
            isFirstMediaVideo = mediaBitmaps.firstOrNull()?.isVideo == true,
        )
    }

    private fun measureRelatedMediaHeight(
        contentWidth: Float,
        mediaBitmaps: List<ExportMediaBitmap>,
    ): Float {
        return relatedMediaHeightFor(
            contentWidth = contentWidth,
            mediaCount = mediaBitmaps.size,
            firstMediaWidth = mediaBitmaps.firstOrNull()?.bitmap?.width,
            firstMediaHeight = mediaBitmaps.firstOrNull()?.bitmap?.height,
            isFirstMediaVideo = mediaBitmaps.firstOrNull()?.isVideo == true,
        )
    }

    private fun drawPlatformMark(
        canvas: Canvas,
        platform: SocialPlatform,
        rect: RectF,
        color: Int,
    ) {
        val logoPathData = platformLogoPathData(platform)
        if (logoPathData != null) {
            drawPlatformLogoPath(canvas, rect, color, logoPathData)
            return
        }

        drawCenteredText(
            canvas = canvas,
            text = platform.label,
            centerX = rect.centerX(),
            centerY = rect.centerY(),
            paint = textPaint(color, 33f, Typeface.SANS_SERIF, bold = true),
        )
    }

    private fun drawPlatformLogoPath(
        canvas: Canvas,
        rect: RectF,
        color: Int,
        pathData: String,
    ) {
        val path = PathParser.createPathFromPathData(pathData)
        val scale = min(rect.width(), rect.height()) / 24f
        val width = 24f * scale
        val height = 24f * scale
        val matrix =
            Matrix().apply {
                postScale(scale, scale)
                postTranslate(
                    rect.left + ((rect.width() - width) / 2f),
                    rect.top + ((rect.height() - height) / 2f),
                )
            }
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }

        path.transform(matrix)
        canvas.drawPath(path, paint)
    }

    private fun drawMedia(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
        palette: RenderPalette,
        cropSingle: Boolean = false,
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
            if (cropSingle) {
                drawImageCover(canvas, mediaBitmaps.first().bitmap, rect)
            } else {
                drawImageFit(canvas, mediaBitmaps.first().bitmap, rect)
            }
        } else {
            drawMediaGrid(canvas, mediaBitmaps.take(4), rect)
        }

        canvas.restore()
        canvas.drawRoundRect(rect, 42f, 42f, strokePaint)
    }

    private fun List<ExportMediaBitmap>.shouldCoverSingleVideo(): Boolean =
        size == 1 && first().isVideo

    private fun drawCircularImage(
        canvas: Canvas,
        bitmap: Bitmap,
        rect: RectF,
    ) {
        canvas.save()
        canvas.clipPath(
            Path().apply {
                addOval(rect, Path.Direction.CW)
            },
        )
        drawImageCover(canvas, bitmap, rect)
        canvas.restore()
    }

    private fun drawMediaGrid(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
    ) {
        when (mediaBitmaps.size) {
            2 -> drawTwoMediaGrid(canvas, mediaBitmaps, rect)
            3 -> drawThreeMediaGrid(canvas, mediaBitmaps, rect)
            else -> drawFourMediaGrid(canvas, mediaBitmaps, rect)
        }
    }

    private fun drawTwoMediaGrid(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
    ) {
        val cellWidth = (rect.width() - MediaGridGap) / 2f

        mediaBitmaps.take(2).forEachIndexed { index, media ->
            val left = rect.left + index * (cellWidth + MediaGridGap)
            drawMediaGridCell(
                canvas = canvas,
                media = media,
                rect = RectF(left, rect.top, left + cellWidth, rect.bottom),
            )
        }
    }

    private fun drawThreeMediaGrid(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
    ) {
        val cellWidth = (rect.width() - MediaGridGap) / 2f
        val rightLeft = rect.left + cellWidth + MediaGridGap
        val rightCellHeight = (rect.height() - MediaGridGap) / 2f

        drawMediaGridCell(
            canvas = canvas,
            media = mediaBitmaps[0],
            rect = RectF(rect.left, rect.top, rect.left + cellWidth, rect.bottom),
        )
        drawMediaGridCell(
            canvas = canvas,
            media = mediaBitmaps[1],
            rect = RectF(rightLeft, rect.top, rect.right, rect.top + rightCellHeight),
        )
        drawMediaGridCell(
            canvas = canvas,
            media = mediaBitmaps[2],
            rect = RectF(rightLeft, rect.top + rightCellHeight + MediaGridGap, rect.right, rect.bottom),
        )
    }

    private fun drawFourMediaGrid(
        canvas: Canvas,
        mediaBitmaps: List<ExportMediaBitmap>,
        rect: RectF,
    ) {
        val cellWidth = (rect.width() - MediaGridGap) / 2f
        val rowHeight = (rect.height() - MediaGridGap) / 2f

        mediaBitmaps.take(4).forEachIndexed { index, media ->
            val column = index % 2
            val row = index / 2
            val left = rect.left + column * (cellWidth + MediaGridGap)
            val top = rect.top + row * (rowHeight + MediaGridGap)
            drawMediaGridCell(
                canvas = canvas,
                media = media,
                rect = RectF(left, top, left + cellWidth, top + rowHeight),
            )
        }
    }

    private fun drawMediaGridCell(
        canvas: Canvas,
        media: ExportMediaBitmap,
        rect: RectF,
    ) {
        drawImageCover(canvas, media.bitmap, rect)
    }

    private fun drawImageCover(
        canvas: Canvas,
        bitmap: Bitmap,
        rect: RectF,
    ) {
        val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = rect.left + ((rect.width() - width) / 2f)
        val top = rect.top + ((rect.height() - height) / 2f)
        val destination = RectF(left, top, left + width, top + height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(bitmap, null, destination, paint)
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
    val mediaWidth: Float,
    val mediaHeight: Float,
    val authorAvatar: Bitmap?,
    val authorNameLayout: StaticLayout,
    val authorNameVisible: Boolean,
    val authorHandleLayout: StaticLayout,
    val authorHandleVisible: Boolean,
    val markLayout: StaticLayout,
    val footerHeight: Float,
    val height: Int,
)

private data class MeasuredRelatedPost(
    val authorAvatar: Bitmap?,
    val authorLayout: StaticLayout,
    val contentLayout: StaticLayout,
    val mediaHeight: Float,
) {
    val headerHeight: Float
        get() = max(authorLayout.height.toFloat(), if (authorAvatar != null) RelatedAvatarSize else 0f)

    val height: Float
        get() {
            val mediaBlockHeight =
                if (mediaHeight > 0f) {
                    SmallGap + mediaHeight
                } else {
                    0f
                }
            return RelatedPadding + headerHeight + SmallGap + contentLayout.height.toFloat() + mediaBlockHeight +
                RelatedPadding
        }
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

private data class ExportMediaBitmaps(
    val main: List<ExportMediaBitmap> = emptyList(),
    val related: List<ExportMediaBitmap> = emptyList(),
) {
    fun recycle() {
        main.forEach { media -> media.bitmap.recycle() }
        related.forEach { media -> media.bitmap.recycle() }
    }

    companion object {
        val Empty = ExportMediaBitmaps()
    }
}

private data class ExportAvatarBitmaps(
    val author: Bitmap?,
    val related: Bitmap?,
) {
    fun recycle() {
        author?.recycle()
        related?.recycle()
    }
}

private data class VideoExportSize(
    val width: Int,
    val height: Int,
)

internal data class ExportMediaFrameSize(
    val width: Float,
    val height: Float,
)

internal data class VideoExportProfile(
    val frameRate: Int,
    val bitmapWidth: Int,
)

internal fun videoExportProfileForDurationMs(durationMs: Long): VideoExportProfile =
    if (durationMs >= VideoExportLongClipThresholdMs) {
        VideoExportProfile(
            frameRate = VideoExportLongClipFrameRate,
            bitmapWidth = VideoExportLongClipBitmapWidth,
        )
    } else {
        VideoExportProfile(
            frameRate = VideoExportFrameRate,
            bitmapWidth = VideoExportBitmapWidth,
        )
    }

private data class MediaRects(
    val main: RectF? = null,
    val related: RectF? = null,
)

private data class ExportMediaSources(
    val main: List<ExportMediaSource>,
    val related: List<ExportMediaSource>,
) {
    val all: List<ExportMediaSource>
        get() = main + related

    fun videoSourceFor(selectedSourceId: String?): ExportMediaSource? {
        return selectedSourceId
            ?.let { sourceId ->
                all.firstOrNull { source -> source.sourceId == sourceId && source.playableVideoUrl != null }
            }
            ?: all.firstOrNull { source -> source.playableVideoUrl != null }
    }
}

private data class VideoInputSource(
    val videoSource: String,
    val audioSource: String?,
    val durationMs: Long?,
    val localFiles: List<File> = emptyList(),
)

private data class ExportVideoMediaSlots(
    val main: List<ExportVideoMediaSlot>,
    val related: List<ExportVideoMediaSlot>,
) {
    fun recycle() {
        main.recycleVideoSlots()
        related.recycleVideoSlots()
    }
}

private data class ExportMediaSource(
    val sourceId: String,
    val url: String,
    val imageUrls: List<String> = listOf(url),
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
        .setEllipsize(if (maxLines != Int.MAX_VALUE) TextUtils.TruncateAt.END else null)
        .build()

private fun String?.displayTextOrNull(): String? = this?.trim()?.takeIf { value -> value.isNotEmpty() }

private fun RelatedPost.authorLabel(): String =
    "$ReplyRelationshipLabel " + listOfNotNull(authorName.displayTextOrNull(), authorHandle.displayTextOrNull())
        .joinToString("  ")
        .ifBlank { "Original post" }

private fun List<PostMedia>.exportMediaSources(): List<ExportMediaSource> = mapNotNull(PostMedia::exportSource)

private fun PostMedia.exportSource(): ExportMediaSource? {
    return when (this) {
        is PostMedia.Image ->
            ExportMediaSource(
                sourceId = url,
                url = url,
                imageUrls = (listOf(url) + variants).distinct(),
                isVideo = false,
            )

        is PostMedia.Video -> {
            val playableUrl = playableVideoUrl()
            (posterUrl ?: playableUrl ?: url ?: variants.firstOrNull())
                ?.let { previewUrl ->
                    ExportMediaSource(
                        sourceId = playableUrl ?: previewUrl,
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
    val selectedMp4 = directMp4Urls
        .filter { url -> url.videoResolution()?.longEdge()?.let { it <= VideoExportSourceVariantMaxLongEdge } ?: false }
        .maxByOrNull { url -> url.videoResolution()?.area ?: 0 }
        ?: directMp4Urls.minByOrNull { url -> url.videoResolution()?.longEdge() ?: Int.MAX_VALUE }

    return selectedMp4 ?: candidates.distinct().firstOrNull { candidate -> candidate.isHlsVideoUrl() }
}

private fun String.isDirectMp4VideoUrl(): Boolean {
    return startsWith("https://") &&
        ".mp4" in this &&
        !contains("/pl/") &&
        !contains("/seg/")
}

private fun String.isHlsVideoUrl(): Boolean {
    return startsWith("https://") && ".m3u8" in this
}

internal data class HlsPlaylistSelection(
    val videoPlaylistUrl: String,
    val audioPlaylistUrl: String?,
)

internal fun selectHlsMediaPlaylistsForExport(
    masterPlaylist: String,
    playlistUrl: String,
): HlsPlaylistSelection? {
    val lines = masterPlaylist.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val audioRenditions =
        lines
            .filter { line -> line.startsWith("#EXT-X-MEDIA:") }
            .mapNotNull { line ->
                val attributes = parseHlsAttributes(line)
                if (attributes["TYPE"] != "AUDIO") {
                    return@mapNotNull null
                }
                val groupId = attributes["GROUP-ID"] ?: return@mapNotNull null
                val uri = attributes["URI"] ?: return@mapNotNull null
                HlsAudioRendition(
                    groupId = groupId,
                    playlistUrl = resolveHlsUrl(playlistUrl, uri),
                )
            }
    val variants =
        lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) {
                return@mapIndexedNotNull null
            }

            val mediaUri =
                lines
                    .drop(index + 1)
                    .firstOrNull { nextLine -> !nextLine.startsWith("#") }
                    ?: return@mapIndexedNotNull null
            val attributes = parseHlsAttributes(line)
            HlsVariant(
                playlistUrl = resolveHlsUrl(playlistUrl, mediaUri),
                resolution = attributes["RESOLUTION"]?.toVideoResolution(),
                bandwidth = attributes["AVERAGE-BANDWIDTH"]?.toIntOrNull()
                    ?: attributes["BANDWIDTH"]?.toIntOrNull()
                    ?: 0,
                audioGroupId = attributes["AUDIO"],
            )
        }

    val selectedVariant =
        variants
            .filter { variant ->
                variant.resolution?.longEdge()?.let { longEdge ->
                    longEdge <= VideoExportSourceVariantMaxLongEdge
                } ?: false
            }
            .maxWithOrNull(compareBy<HlsVariant> { it.resolution?.area ?: 0 }.thenBy { it.bandwidth })
            ?: variants.minWithOrNull(compareBy<HlsVariant> { it.resolution?.longEdge() ?: Int.MAX_VALUE }.thenByDescending { it.bandwidth })
            ?: return null

    val audioPlaylistUrl =
        audioRenditions
            .firstOrNull { rendition -> rendition.groupId == selectedVariant.audioGroupId }
            ?.playlistUrl

    return HlsPlaylistSelection(
        videoPlaylistUrl = selectedVariant.playlistUrl,
        audioPlaylistUrl = audioPlaylistUrl,
    )
}

internal fun hlsExportDurationMsForMediaPlaylist(
    playlist: String,
    maxDurationMs: Long = VideoExportMaxDurationMs,
): Long {
    var pendingDurationSeconds = 0.0
    var totalDurationSeconds = 0.0
    val maxDurationSeconds = maxDurationMs / 1_000.0

    playlist
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { line ->
            when {
                line.startsWith("#EXTINF:") -> {
                    pendingDurationSeconds =
                        line
                            .substringAfter(":")
                            .substringBefore(",")
                            .toDoubleOrNull()
                            ?: 0.0
                }

                line.startsWith("#") -> Unit

                totalDurationSeconds < maxDurationSeconds -> {
                    totalDurationSeconds += pendingDurationSeconds
                    pendingDurationSeconds = 0.0
                }
            }
        }

    return (totalDurationSeconds * 1_000.0)
        .roundToLong()
        .coerceAtLeast(1_000L)
}

internal fun hlsExportSegmentCountForMediaPlaylist(
    playlist: String,
    maxDurationMs: Long = VideoExportMaxDurationMs,
): Int {
    var pendingDurationSeconds = 0.0
    var totalDurationSeconds = 0.0
    var segmentCount = 0
    val maxDurationSeconds = maxDurationMs / 1_000.0

    playlist
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { line ->
            when {
                line.startsWith("#EXTINF:") -> {
                    pendingDurationSeconds =
                        line
                            .substringAfter(":")
                            .substringBefore(",")
                            .toDoubleOrNull()
                            ?: 0.0
                }

                line.startsWith("#") -> Unit

                totalDurationSeconds < maxDurationSeconds -> {
                    totalDurationSeconds += pendingDurationSeconds
                    pendingDurationSeconds = 0.0
                    segmentCount++
                }
            }
        }

    return segmentCount
}

private data class HlsVariant(
    val playlistUrl: String,
    val resolution: VideoResolution?,
    val bandwidth: Int,
    val audioGroupId: String?,
)

private data class HlsAudioRendition(
    val groupId: String,
    val playlistUrl: String,
)

private fun parseHlsAttributes(line: String): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    val rawAttributes = line.substringAfter(":", missingDelimiterValue = "")
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    rawAttributes.forEach { char ->
        when (char) {
            '"' -> {
                inQuotes = !inQuotes
                current.append(char)
            }
            ',' -> {
                if (inQuotes) {
                    current.append(char)
                } else {
                    parts += current.toString()
                    current.clear()
                }
            }
            else -> current.append(char)
        }
    }
    if (current.isNotEmpty()) {
        parts += current.toString()
    }

    parts.forEach { part ->
        val key = part.substringBefore("=", missingDelimiterValue = "").trim()
        val value = part.substringAfter("=", missingDelimiterValue = "").trim().trim('"')
        if (key.isNotEmpty() && value.isNotEmpty()) {
            attributes[key] = value
        }
    }

    return attributes
}

private fun String.toVideoResolution(): VideoResolution? {
    val width = substringBefore("x").toIntOrNull() ?: return null
    val height = substringAfter("x", missingDelimiterValue = "").toIntOrNull() ?: return null
    return VideoResolution(width = width, height = height)
}

private fun resolveHlsUrl(
    playlistUrl: String,
    mediaUri: String,
): String = URL(URL(playlistUrl), mediaUri).toString()

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

internal fun relatedMediaHeightFor(
    contentWidth: Float,
    mediaCount: Int,
    firstMediaWidth: Int?,
    firstMediaHeight: Int?,
    isFirstMediaVideo: Boolean = false,
): Float {
    if (mediaCount <= 0) {
        return RelatedMediaThumbnailSize
    }

    if (mediaCount == 1) {
        val ratio =
            firstMediaWidth
                ?.takeIf { width -> width > 0 }
                ?.let { width ->
                    val height = firstMediaHeight?.takeIf { value -> value > 0 } ?: return@let null
                    width.toFloat() / height.toFloat()
                }
                ?: PlaceholderMediaAspectRatio

        val mediaHeight = max(RelatedMediaMinHeight, contentWidth / ratio.coerceIn(0.56f, 2.35f))
        return if (isFirstMediaVideo) {
            min(mediaHeight, RelatedVideoMediaMaxHeight)
        } else {
            mediaHeight
        }
    }

    return if (mediaCount == 2) {
        max(RelatedMediaMinHeight, contentWidth / TwoMediaGridAspectRatio)
    } else {
        max(RelatedMediaMinHeight, contentWidth / MultiMediaGridAspectRatio)
    }
}

internal fun mainMediaFrameSizeFor(
    contentWidth: Float,
    mediaCount: Int,
    firstMediaWidth: Int?,
    firstMediaHeight: Int?,
    isFirstMediaVideo: Boolean = false,
): ExportMediaFrameSize {
    if (mediaCount <= 0) {
        return ExportMediaFrameSize(
            width = contentWidth,
            height = max(MinMediaHeight, contentWidth / PlaceholderMediaAspectRatio),
        )
    }

    if (mediaCount == 1) {
        val sourceRatio =
            firstMediaWidth
                ?.takeIf { width -> width > 0 }
                ?.let { width ->
                    val height = firstMediaHeight?.takeIf { value -> value > 0 } ?: return@let null
                    width.toFloat() / height.toFloat()
                }
                ?: PlaceholderMediaAspectRatio

        val maxVideoHeight =
            if (isFirstMediaVideo) {
                mainVideoMediaMaxHeight(
                    mediaRatio = (firstMediaHeight?.toFloat() ?: 0f) /
                        (firstMediaWidth?.toFloat()?.coerceAtLeast(1f) ?: 1f),
                )
            } else {
                null
            }

        if (maxVideoHeight != null) {
            val targetRatio = sourceRatio.coerceIn(TallVideoMediaMinAspectRatio, 2.35f)
            val naturalHeight = max(MinMediaHeight, contentWidth / targetRatio)
            val frameHeight = min(naturalHeight, maxVideoHeight)
            val frameWidth = min(contentWidth, frameHeight * targetRatio)
            return ExportMediaFrameSize(
                width = frameWidth,
                height = frameHeight,
            )
        }

        val mediaHeight = max(MinMediaHeight, contentWidth / sourceRatio.coerceIn(0.62f, 2.35f))
        return ExportMediaFrameSize(
            width = contentWidth,
            height = mediaHeight,
        )
    }

    return ExportMediaFrameSize(
        width = contentWidth,
        height =
            if (mediaCount == 2) {
                max(MinMediaHeight, contentWidth / TwoMediaGridAspectRatio)
            } else {
                max(MinMediaHeight, contentWidth / MultiMediaGridAspectRatio)
            },
    )
}

private fun mainVideoMediaMaxHeight(mediaRatio: Float): Float? {
    if (mediaRatio >= 1.45f) {
        return TallVideoMediaMaxHeight
    }

    if (mediaRatio >= 1.18f) {
        return MediumTallVideoMediaMaxHeight
    }

    return null
}

private fun findAudioTrackFormat(sourcePath: String): MediaFormat? {
    val extractor = MediaExtractor()
    return try {
        extractor.setVideoDataSource(sourcePath)
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
    ensureActive: () -> Unit = {},
) {
    val extractor = MediaExtractor()
    val bufferInfo = MediaCodec.BufferInfo()
    try {
        extractor.setVideoDataSource(sourcePath)
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
            ensureActive()
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

private fun MediaExtractor.setVideoDataSource(source: String) {
    if (source.isRemoteVideoUrl()) {
        setDataSource(source, VideoRequestHeaders)
    } else {
        setDataSource(source)
    }
}

private fun MediaMetadataRetriever.setVideoDataSource(source: String) {
    if (source.isRemoteVideoUrl()) {
        setDataSource(source, VideoRequestHeaders)
    } else {
        setDataSource(source)
    }
}

private fun String.isRemoteVideoUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

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
