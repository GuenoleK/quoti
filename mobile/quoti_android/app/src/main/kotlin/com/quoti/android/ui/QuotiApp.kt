package com.quoti.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.quoti.android.core.model.CardContentMode
import com.quoti.android.core.model.CardTone
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.SocialPlatform
import com.quoti.android.core.model.hasMedia
import com.quoti.android.data.GalleryFixtureRepository
import com.quoti.android.export.QuotiCardExporter
import com.quoti.android.share.IncomingShareDraft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.launch

data class QuotiUiSettings(
    val cardTone: CardTone = CardTone.Light,
    val contentMode: CardContentMode = CardContentMode.WithMedia,
    val sourceActionsEnabled: Boolean = true,
)

private sealed interface GalleryLoadState {
    data class Ready(val posts: List<QuotiPost>) : GalleryLoadState
    data class Failed(val message: String) : GalleryLoadState
}

private val FallbackPreviewPost =
    QuotiPost(
        id = "fallback-preview",
        platform = SocialPlatform.X,
        authorName = "Maya Laurent",
        authorHandle = "@maya_laurent",
        content =
            "The best product moments are quiet: the user brings the context, and the tool makes it travel cleanly.",
        relatedPost =
            RelatedPost(
                authorName = "Dexerto",
                authorHandle = "@Dexerto",
                content =
                    "Viral dopamine sites are letting users shop without actually spending money.",
            ),
        publishedAt = "2026-06-16T10:00:00Z",
        sourceUrl = "https://x.com/dexerto/status/123",
        capturedAt = "2026-06-16T10:00:00Z",
    )

@Composable
fun QuotiApp(incomingDraft: IncomingShareDraft?) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val galleryState by produceState<GalleryLoadState>(
        initialValue = GalleryLoadState.Ready(listOf(FallbackPreviewPost)),
    ) {
        value =
            runCatching { GalleryFixtureRepository(context.assets).loadPosts() }
                .fold(
                    onSuccess = { posts ->
                        GalleryLoadState.Ready(posts.ifEmpty { listOf(FallbackPreviewPost) })
                    },
                    onFailure = { error ->
                        GalleryLoadState.Failed(error.message ?: "Gallery unavailable")
                    },
                )
    }
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
    val galleryPost =
        when (val state = galleryState) {
            is GalleryLoadState.Ready -> selectPreviewPost(state.posts) ?: FallbackPreviewPost
            is GalleryLoadState.Failed -> FallbackPreviewPost
        }
    val post = incomingDraft?.post ?: galleryPost

    LaunchedEffect(incomingDraft?.post?.id) {
        if (incomingDraft != null) {
            snackbarHostState.showSnackbar("Shared post captured")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        QuotiCaptureScreen(
            post = post,
            settings = settings,
            onCardToneChange = { cardTone = it },
            onContentModeChange = { contentMode = it },
            onSettingsClick = { showSettings = true },
            onCopyImage = {
                scope.launch {
                    runCatching {
                        copyCardImage(
                            context = context,
                            clipboard = clipboard,
                            rootView = rootView,
                            cardBounds = requireNotNull(cardBounds) { "Card is not laid out yet." },
                            post = post,
                        )
                    }.fold(
                        onSuccess = {
                            snackbarHostState.showSnackbar(
                                if (post.sourceUrl == null) {
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
                    snackbarHostState.showSnackbar("Video download is next")
                }
            },
            onDownloadPng = {
                scope.launch {
                    runCatching {
                        QuotiCardExporter.writePicturesPng(
                            context = context,
                            rootView = rootView,
                            cardBounds = requireNotNull(cardBounds) { "Card is not laid out yet." },
                            postId = post.id,
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
                scope.launch {
                    runCatching {
                        shareCardImage(
                            context = context,
                            rootView = rootView,
                            cardBounds = requireNotNull(cardBounds) { "Card is not laid out yet." },
                            post = post,
                        )
                    }.onFailure {
                        snackbarHostState.showSnackbar("Unable to share image")
                    }
                }
            },
            onCopyText = {
                clipboard.setPrimaryClip(ClipData.newPlainText("Quoti text", post.content))
                scope.launch {
                    snackbarHostState.showSnackbar("Text copied")
                }
            },
            onCopySource = {
                val sourceUrl = post.sourceUrl
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
                val sourceUrl = post.sourceUrl
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
            onCardBoundsChange = { cardBounds = it },
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
    post: QuotiPost,
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
    onCardBoundsChange: (Rect) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
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
            PreviewFrame(
                post = post,
                cardTone = settings.cardTone,
                contentMode = settings.contentMode,
                onCardBoundsChange = onCardBoundsChange,
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
    onCardBoundsChange: (Rect) -> Unit,
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
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        onCardBoundsChange(coordinates.boundsInRoot().toAndroidRect())
                    },
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
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                )
            }

            if (contentMode == CardContentMode.WithMedia && post.hasMedia) {
                MediaPlaceholder(
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

private fun ComposeRect.toAndroidRect(): Rect =
    Rect(
        floor(left).toInt(),
        floor(top).toInt(),
        ceil(right).toInt(),
        ceil(bottom).toInt(),
    )

@Composable
private fun RelatedPostBlock(
    relatedPost: RelatedPost,
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
            text = author,
            style = MaterialTheme.typography.labelLarge,
            color = mutedColor,
        )
        Text(
            text = relatedPost.content,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
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
    val hasVideo = post.containsVideo()
    val primaryActionLabel = if (hasVideo) "Download video" else "Copy image"
    val primaryAction = if (hasVideo) onDownloadVideo else onCopyImage
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
                        hasVideo = hasVideo,
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
    hasVideo: Boolean,
    contentDescription: String,
) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (hasVideo) Icons.Outlined.Movie else Icons.Outlined.Image,
            contentDescription = contentDescription,
            modifier = Modifier.size(26.dp),
        )
        Icon(
            imageVector = if (hasVideo) Icons.Outlined.Download else Icons.Outlined.ContentCopy,
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
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun selectPreviewPost(posts: List<QuotiPost>): QuotiPost? {
    return posts.firstOrNull { it.relatedPost != null } ?: posts.firstOrNull()
}

private fun QuotiPost.containsVideo(): Boolean {
    return media.any { it is PostMedia.Video } ||
        relatedPost?.media?.any { it is PostMedia.Video } == true
}

private suspend fun copyCardImage(
    context: Context,
    clipboard: ClipboardManager,
    rootView: android.view.View,
    cardBounds: Rect,
    post: QuotiPost,
) {
    val uri =
        QuotiCardExporter.writeCachePng(
            context = context,
            rootView = rootView,
            cardBounds = cardBounds,
            postId = post.id,
        )
    val clipData = ClipData.newUri(context.contentResolver, "Quoti card image", uri)
    post.sourceUrl?.let { sourceUrl ->
        clipData.addItem(ClipData.Item(sourceUrl))
    }
    clipboard.setPrimaryClip(clipData)
}

private suspend fun shareCardImage(
    context: Context,
    rootView: android.view.View,
    cardBounds: Rect,
    post: QuotiPost,
) {
    val uri: Uri =
        QuotiCardExporter.writeCachePng(
            context = context,
            rootView = rootView,
            cardBounds = cardBounds,
            postId = post.id,
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
