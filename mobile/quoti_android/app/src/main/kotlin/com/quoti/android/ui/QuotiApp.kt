package com.quoti.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarHorizontalFabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.quoti.android.BuildConfig
import com.quoti.android.core.model.CardContentMode
import com.quoti.android.core.model.CardTone
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.export.QuotiCardExporter
import com.quoti.android.share.IncomingShareDraft
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class QuotiUiSettings(
    val cardTone: CardTone = CardTone.Light,
    val contentMode: CardContentMode = CardContentMode.WithMedia,
    val sourceActionsEnabled: Boolean = true,
)

sealed interface QuotiShareState {
    data object Empty : QuotiShareState

    data class Loading(
        val sourceUrl: String? = null,
    ) : QuotiShareState

    data class Ready(
        val draft: IncomingShareDraft,
    ) : QuotiShareState
}

@Composable
fun QuotiApp(incomingDraft: IncomingShareDraft?) {
    QuotiApp(
        shareState =
            incomingDraft
                ?.let(QuotiShareState::Ready)
                ?: QuotiShareState.Empty,
    )
}

@Composable
fun QuotiApp(shareState: QuotiShareState) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var cardTone by rememberSaveable { mutableStateOf(CardTone.Light) }
    var contentMode by rememberSaveable { mutableStateOf(CardContentMode.WithMedia) }
    var sourceActionsEnabled by rememberSaveable { mutableStateOf(true) }
    val settings =
        QuotiUiSettings(
            cardTone = cardTone,
            contentMode = contentMode,
            sourceActionsEnabled = sourceActionsEnabled,
        )
    val post = (shareState as? QuotiShareState.Ready)?.draft?.post

    LaunchedEffect(post?.id) {
        if (post != null) {
            snackbarHostState.showSnackbar("Shared post captured")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        QuotiCaptureScreen(
            shareState = shareState,
            settings = settings,
            onCardToneChange = { cardTone = it },
            onContentModeChange = { contentMode = it },
            onSettingsClick = { showSettings = true },
            onCopyImage = {
                val activePost = post ?: return@QuotiCaptureScreen
                scope.launch {
                    runCatching {
                        copyCardImage(
                            context = context,
                            clipboard = clipboard,
                            post = activePost,
                            settings = settings,
                        )
                    }.fold(
                        onSuccess = {
                            snackbarHostState.showSnackbar(
                                if (activePost.sourceUrl == null) {
                                    "Image copied"
                                } else {
                                    "Image and source link copied"
                                },
                            )
                        },
                        onFailure = {
                            snackbarHostState.showSnackbar("Unable to copy image")
                        },
                    )
                }
            },
            onDownloadVideo = {
                scope.launch {
                    snackbarHostState.showSnackbar("Video export is not ready yet")
                }
            },
            onDownloadPng = {
                val activePost = post ?: return@QuotiCaptureScreen
                scope.launch {
                    runCatching {
                        QuotiCardExporter.writePicturesPng(
                            context = context,
                            post = activePost,
                            cardTone = settings.cardTone,
                            contentMode = settings.contentMode,
                        )
                    }.fold(
                        onSuccess = {
                            snackbarHostState.showSnackbar("PNG saved to Pictures/Quoti")
                        },
                        onFailure = {
                            snackbarHostState.showSnackbar("Unable to save PNG")
                        },
                    )
                }
            },
            onShareImage = {
                val activePost = post ?: return@QuotiCaptureScreen
                scope.launch {
                    runCatching {
                        shareCardImage(
                            context = context,
                            post = activePost,
                            settings = settings,
                        )
                    }.onFailure {
                        snackbarHostState.showSnackbar("Unable to share image")
                    }
                }
            },
            onCopyText = {
                val activePost = post ?: return@QuotiCaptureScreen
                clipboard.setPrimaryClip(ClipData.newPlainText("Quoti text", activePost.content))
                scope.launch {
                    snackbarHostState.showSnackbar("Text copied")
                }
            },
            onCopySource = {
                val sourceUrl = post?.sourceUrl
                if (sourceUrl == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No source URL")
                    }
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Quoti source", sourceUrl))
                    scope.launch {
                        snackbarHostState.showSnackbar("Source copied")
                    }
                }
            },
            onOpenSource = {
                val sourceUrl = post?.sourceUrl
                if (sourceUrl == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No source URL")
                    }
                } else {
                    uriHandler.openUri(sourceUrl)
                }
            },
            onRefresh = {
                Toast.makeText(
                    context,
                    "Share to Quoti from Android to update the card.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            contentPadding = innerPadding,
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onSourceActionsChange = { sourceActionsEnabled = it },
            onReset = {
                cardTone = CardTone.Light
                contentMode = CardContentMode.WithMedia
                sourceActionsEnabled = true
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun QuotiCaptureScreen(
    shareState: QuotiShareState,
    settings: QuotiUiSettings,
    onCardToneChange: (CardTone) -> Unit,
    onContentModeChange: (CardContentMode) -> Unit,
    onSettingsClick: () -> Unit,
    onCopyImage: () -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadPng: () -> Unit,
    onShareImage: () -> Unit,
    onCopyText: () -> Unit,
    onCopySource: () -> Unit,
    onOpenSource: () -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val readyPost = (shareState as? QuotiShareState.Ready)?.draft?.post

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        readyPost?.let { post ->
            QuotiActionToolbar(
                post = post,
                sourceActionsEnabled = settings.sourceActionsEnabled,
                onCopyImage = onCopyImage,
                onDownloadVideo = onDownloadVideo,
                onDownloadPng = onDownloadPng,
                onShareImage = onShareImage,
                onCopyText = onCopyText,
                onCopySource = onCopySource,
                onOpenSource = onOpenSource,
                onRefresh = onRefresh,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 18.dp)
                        .zIndex(1f),
            )
        }

        Column(
            modifier =
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .padding(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(onSettingsClick = onSettingsClick)
            when (shareState) {
                QuotiShareState.Empty -> EmptyCaptureState()
                is QuotiShareState.Loading -> LoadingCaptureState()
                is QuotiShareState.Ready -> {
                    PreviewFrame(
                        post = shareState.draft.post,
                        cardTone = settings.cardTone,
                        contentMode = settings.contentMode,
                    )
                    ExpressiveChoiceGroup(
                        value = settings.cardTone,
                        options =
                            listOf(
                                SegmentOption(CardTone.Light, "Light"),
                                SegmentOption(CardTone.Dark, "Dark"),
                            ),
                        onValueChange = onCardToneChange,
                    )
                    ExpressiveChoiceGroup(
                        value = settings.contentMode,
                        options =
                            listOf(
                                SegmentOption(CardContentMode.TextOnly, "Text only"),
                                SegmentOption(CardContentMode.WithMedia, "With media"),
                            ),
                        onValueChange = onContentModeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCaptureState() {
    StateFrame {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No post captured",
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            text = "Waiting for a shared post.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingCaptureState() {
    StateFrame {
        CircularProgressIndicator(
            modifier = Modifier.size(42.dp),
            strokeWidth = 4.dp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Preparing post",
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}

@Composable
private fun StateFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun Header(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            color = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Q",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Quoti",
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text = "Capture the post. Keep the context.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
            )
        }
    }
}

@Composable
private fun PreviewFrame(
    post: QuotiPost,
    cardTone: CardTone,
    contentMode: CardContentMode,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            QuotiCardPreview(
                post = post,
                cardTone = cardTone,
                contentMode = contentMode,
            )
        }
    }
}

@Composable
private fun QuotiCardPreview(
    post: QuotiPost,
    cardTone: CardTone,
    contentMode: CardContentMode,
    modifier: Modifier = Modifier,
) {
    val dark = cardTone == CardTone.Dark
    val containerColor = if (dark) Color(0xFF211A16) else Color(0xFFFFFBF6)
    val contentColor = if (dark) Color(0xFFF7E8DC) else Color(0xFF211A16)
    val mutedColor = contentColor.copy(alpha = 0.68f)
    val dividerColor = contentColor.copy(alpha = 0.14f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.12f),
                    contentColor = contentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = post.platform.label,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDate(post.publishedAt ?: post.capturedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedColor,
                )
            }

            Text(
                text = post.content,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
            )

            post.relatedPost?.let { relatedPost ->
                RelatedPostBlock(
                    relatedPost = relatedPost,
                    showMedia = contentMode == CardContentMode.WithMedia,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                )
            }

            if (contentMode == CardContentMode.WithMedia && post.media.isNotEmpty()) {
                RemoteMedia(
                    media = post.media,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                )
            }

            HorizontalDivider(color = dividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = contentColor,
                    )
                    Text(
                        text = post.authorHandle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedColor,
                    )
                }
                Text(
                    text = "Quoti",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (dark) Color(0xFFFFC7A8) else Color(0xFF7A442F),
                )
            }
        }
    }
}

@Composable
private fun RelatedPostBlock(
    relatedPost: RelatedPost,
    showMedia: Boolean,
    contentColor: Color,
    mutedColor: Color,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = contentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val author =
            listOfNotNull(relatedPost.authorName, relatedPost.authorHandle)
                .joinToString("  ")
                .ifBlank { "Original post" }
        Text(
            text = "Répond à $author",
            style = MaterialTheme.typography.labelLarge,
            color = mutedColor,
        )
        Text(
            text = relatedPost.content,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
        if (showMedia && relatedPost.media.isNotEmpty()) {
            RemoteMedia(
                media = relatedPost.media,
                contentColor = contentColor,
                mutedColor = mutedColor,
            )
        }
    }
}

@Composable
private fun RemoteMedia(
    media: List<PostMedia>,
    contentColor: Color,
    mutedColor: Color,
) {
    val sources = media.mapNotNull(PostMedia::previewSource).take(4)
    val mediaKey = sources.joinToString("|") { source -> source.url }
    val loadedMedia by produceState<List<LoadedRemoteMedia>>(initialValue = emptyList(), mediaKey) {
        value =
            sources.mapNotNull { source ->
                loadRemoteBitmap(source.url)?.let { bitmap ->
                    LoadedRemoteMedia(
                        bitmap = bitmap,
                        isVideo = source.isVideo,
                        playableVideoUrl = source.playableVideoUrl,
                    )
                }
            }
    }

    if (sources.isEmpty() || loadedMedia.isEmpty()) {
        MediaPlaceholder(
            contentColor = contentColor,
            mutedColor = mutedColor,
        )
        return
    }

    if (loadedMedia.size == 1) {
        SingleRemoteMedia(
            media = loadedMedia.first(),
            contentColor = contentColor,
        )
    } else {
        RemoteMediaGrid(
            media = loadedMedia,
            contentColor = contentColor,
        )
    }
}

@Composable
private fun SingleRemoteMedia(
    media: LoadedRemoteMedia,
    contentColor: Color,
) {
    val aspectRatio = media.aspectRatio.coerceIn(0.62f, 2.35f)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(contentColor.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (media.isVideo && media.playableVideoUrl != null) {
            VideoPlayer(
                videoUrl = media.playableVideoUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                bitmap = media.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (media.isVideo) {
                VideoBadge()
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ -> true }
                tag = videoUrl
                setVideoURI(Uri.parse(videoUrl))
            }
        },
        update = { view ->
            if (view.tag != videoUrl) {
                view.tag = videoUrl
                view.setVideoURI(Uri.parse(videoUrl))
            }
            if (!view.isPlaying) {
                view.start()
            }
        },
    )
}

@Composable
private fun RemoteMediaGrid(
    media: List<LoadedRemoteMedia>,
    contentColor: Color,
) {
    val visibleMedia = media.take(4)
    val rows = visibleMedia.chunked(2)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (visibleMedia.size == 2) 2f else 1f)
                .clip(RoundedCornerShape(18.dp))
                .background(contentColor.copy(alpha = 0.08f)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { rowMedia ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rowMedia.forEach { item ->
                    MediaGridCell(
                        media = item,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
                if (rowMedia.size == 1 && visibleMedia.size > 1) {
                    Spacer(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGridCell(
    media: LoadedRemoteMedia,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = media.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) {
            VideoBadge()
        }
    }
}

@Composable
private fun VideoBadge() {
    Surface(
        modifier =
            Modifier
                .size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = "Video",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private data class LoadedRemoteMedia(
    val bitmap: Bitmap,
    val isVideo: Boolean,
    val playableVideoUrl: String?,
) {
    val aspectRatio: Float
        get() = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
}

private data class MediaPreviewSource(
    val url: String,
    val isVideo: Boolean,
    val playableVideoUrl: String? = null,
)

private fun PostMedia.previewSource(): MediaPreviewSource? {
    return when (this) {
        is PostMedia.Image -> MediaPreviewSource(url = url, isVideo = false)
        is PostMedia.Video -> {
            val playableUrl = playableVideoUrl()
            (posterUrl ?: playableUrl ?: url ?: variants.firstOrNull())
                ?.let { previewUrl ->
                    MediaPreviewSource(
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
    return candidates.firstOrNull { candidate -> candidate.isPlayableVideoUrl(".mp4") }
        ?: candidates.firstOrNull { candidate -> candidate.isPlayableVideoUrl(".m3u8") }
}

private fun String.isPlayableVideoUrl(extension: String): Boolean {
    return startsWith("https://") && contains(extension)
}

@Composable
private fun MediaPlaceholder(
    contentColor: Color,
    mutedColor: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .aspectRatio(1.85f)
                .clip(RoundedCornerShape(18.dp))
                .background(contentColor.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = mutedColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Media",
                color = mutedColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuotiActionToolbar(
    post: QuotiPost,
    sourceActionsEnabled: Boolean,
    onCopyImage: () -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadPng: () -> Unit,
    onShareImage: () -> Unit,
    onCopyText: () -> Unit,
    onCopySource: () -> Unit,
    onOpenSource: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryActionLabel = "Copy image"
    val primaryAction = onCopyImage
    var overflowExpanded by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = primaryAction,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    PrimaryActionIcon(
                        contentDescription = primaryActionLabel,
                    )
                }
            },
            colors =
                FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    toolbarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fabContainerColor = MaterialTheme.colorScheme.primary,
                    fabContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            floatingActionButtonPosition = FloatingToolbarHorizontalFabPosition.End,
        ) {
            ToolbarActionButton(
                label = "Download PNG",
                onClick = onDownloadPng,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download PNG",
                )
            }
            ToolbarActionButton(
                label = "Copy text",
                onClick = onCopyText,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = "Copy text",
                )
            }
            if (sourceActionsEnabled) {
                ToolbarActionButton(
                    label = "Copy source link",
                    onClick = onCopySource,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = "Copy source link",
                    )
                }
            }
            Box {
                ToolbarActionButton(
                    label = "More actions",
                    onClick = { overflowExpanded = true },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More actions",
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    if (sourceActionsEnabled) {
                        DropdownMenuItem(
                            text = { Text("Open source") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onOpenSource()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share image") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            overflowExpanded = false
                            onShareImage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh preview") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            overflowExpanded = false
                            onRefresh()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionIcon(
    contentDescription: String,
) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = contentDescription,
            modifier = Modifier.size(26.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarActionButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(label)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            icon()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ExpressiveChoiceGroup(
    value: T,
    options: List<SegmentOption<T>>,
    onValueChange: (T) -> Unit,
) {
    val interactionSources = remember(options.size) { List(options.size) { MutableInteractionSource() } }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ButtonGroup(
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            },
            expandedRatio = 0.5f,
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val selected = option.value == value
                val interactionSource = interactionSources[index]

                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = { onValueChange(option.value) },
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex ->
                                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            interactionSource = interactionSource,
                            modifier =
                                Modifier
                                    .widthIn(min = 142.dp)
                                    .height(48.dp)
                                    .animateWidth(
                                        interactionSource = interactionSource,
                                        compressionLimit = ButtonDefaults.ButtonWithIconContentPadding,
                                    )
                                    .semantics { role = Role.RadioButton },
                        ) {
                            Text(
                                text = option.label,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                        }
                    },
                    menuContent = {
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onValueChange(option.value) },
                        )
                    },
                )
            }
        }
    }
}

private data class SegmentOption<T>(
    val value: T,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: QuotiUiSettings,
    onSourceActionsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Source actions") },
                supportingContent = { Text("Show copy source, open source and refresh controls.") },
                trailingContent = {
                    Switch(
                        checked = settings.sourceActionsEnabled,
                        onCheckedChange = onSourceActionsChange,
                    )
                },
            )
            HorizontalDivider(
                color = DividerDefaults.color.copy(alpha = 0.6f),
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onReset,
            ) {
                Text("Reset")
            }
            Text(
                text = "Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun selectPreviewPost(posts: List<QuotiPost>): QuotiPost? {
    return posts.firstOrNull { it.relatedPost != null } ?: posts.firstOrNull()
}

private suspend fun loadRemoteBitmap(imageUrl: String): Bitmap? =
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

private suspend fun copyCardImage(
    context: Context,
    clipboard: ClipboardManager,
    post: QuotiPost,
    settings: QuotiUiSettings,
) {
    val uri =
        QuotiCardExporter.writeCachePng(
            context = context,
            post = post,
            cardTone = settings.cardTone,
            contentMode = settings.contentMode,
        )
    val clipData = ClipData.newUri(context.contentResolver, "Quoti card image", uri)
    post.sourceUrl?.let { sourceUrl ->
        clipData.addItem(ClipData.Item(sourceUrl))
    }
    clipboard.setPrimaryClip(clipData)
}

private suspend fun shareCardImage(
    context: Context,
    post: QuotiPost,
    settings: QuotiUiSettings,
) {
    val uri: Uri =
        QuotiCardExporter.writeCachePng(
            context = context,
            post = post,
            cardTone = settings.cardTone,
            contentMode = settings.contentMode,
        )
    val clipData = ClipData.newUri(context.contentResolver, "Quoti card image", uri)
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            post.sourceUrl?.let { sourceUrl ->
                putExtra(Intent.EXTRA_TEXT, sourceUrl)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            this.clipData = clipData
        }

    context.startActivity(
        Intent.createChooser(shareIntent, "Share Quoti card"),
    )
}

private fun formatDate(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    return DateTimeFormatter
        .ofPattern("MMM d, yyyy", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
