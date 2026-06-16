package com.quoti.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.quoti.android.BuildConfig
import com.quoti.android.core.model.CardContentMode
import com.quoti.android.core.model.CardTone
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.export.QuotiExportType
import com.quoti.android.export.QuotiExportWork
import com.quoti.android.export.QuotiCardExporter
import com.quoti.android.share.IncomingShareDraft
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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
fun QuotiApp(
    incomingDraft: IncomingShareDraft?,
    onClear: () -> Unit = {},
) {
    QuotiApp(
        shareState =
            incomingDraft
                ?.let(QuotiShareState::Ready)
                ?: QuotiShareState.Empty,
        onClear = onClear,
    )
}

@Composable
fun QuotiApp(
    shareState: QuotiShareState,
    onClear: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val workManager = remember(context) { WorkManager.getInstance(context.applicationContext) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var cardTone by rememberSaveable { mutableStateOf(CardTone.Light) }
    var contentMode by rememberSaveable { mutableStateOf(CardContentMode.WithMedia) }
    var sourceActionsEnabled by rememberSaveable { mutableStateOf(true) }
    var activeExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeExportTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var activeExportInfo by remember { mutableStateOf<WorkInfo?>(null) }
    val activeExportType =
        activeExportTypeName
            ?.let { typeName -> runCatching { QuotiExportType.valueOf(typeName) }.getOrNull() }
            ?: QuotiExportType.Video
    val isExportProcessing = activeExportId != null && activeExportInfo?.state?.isFinished != true
    val activeExportProgress =
        activeExportInfo
            ?.progress
            ?.getInt(QuotiExportWork.ProgressPercent, 0)
            ?.coerceIn(0, 100)
            ?: 0
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                scope.launch {
                    snackbarHostState.showSnackbar("Notifications are off. Keep Quoti open to see the result here.")
                }
            }
        }
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

    DisposableEffect(workManager, activeExportId) {
        activeExportInfo = null
        val workId =
            activeExportId
                ?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() }

        if (workId == null) {
            onDispose {}
        } else {
            val liveData = workManager.getWorkInfoByIdLiveData(workId)
            val observer = Observer<WorkInfo?> { info -> activeExportInfo = info }
            liveData.observeForever(observer)
            onDispose { liveData.removeObserver(observer) }
        }
    }

    LaunchedEffect(activeExportInfo?.id, activeExportInfo?.state) {
        val info = activeExportInfo ?: return@LaunchedEffect
        if (!info.state.isFinished) {
            return@LaunchedEffect
        }

        val finishedExportType = activeExportType
        val outputData = info.outputData
        if (info.state == WorkInfo.State.SUCCEEDED) {
            val uri = outputData.getString(QuotiExportWork.OutputUri)?.let(Uri::parse)
            val mimeType = outputData.getString(QuotiExportWork.OutputMimeType)
            if (uri != null && mimeType != null) {
                snackbarHostState.showSavedMediaSnackbar(
                    context = context,
                    message = outputData.getString(QuotiExportWork.OutputMessage)
                        ?: finishedExportType.savedSnackbarMessage,
                    uri = uri,
                    mimeType = mimeType,
                    failureMessage = outputData.getString(QuotiExportWork.OutputFailureMessage)
                        ?: finishedExportType.openFailureSnackbarMessage,
                )
            } else {
                snackbarHostState.showSnackbar(finishedExportType.readySnackbarMessage)
            }
        } else {
            snackbarHostState.showSnackbar(
                outputData.getString(QuotiExportWork.OutputFailureMessage)
                    ?: finishedExportType.failedSnackbarMessage,
            )
        }

        activeExportId = null
        activeExportTypeName = null
        activeExportInfo = null
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startExport(
        activePost: QuotiPost,
        exportType: QuotiExportType,
        exportContentMode: CardContentMode,
    ) {
        if (isExportProcessing) {
            return
        }

        requestNotificationPermissionIfNeeded()
        scope.launch {
            runCatching {
                QuotiExportWork.enqueue(
                    context = context,
                    post = activePost,
                    exportType = exportType,
                    cardTone = settings.cardTone,
                    contentMode = exportContentMode,
                )
            }.fold(
                onSuccess = { workId ->
                    activeExportId = workId.toString()
                    activeExportTypeName = exportType.name
                    snackbarHostState.showSnackbar(exportType.startedSnackbarMessage)
                },
                onFailure = {
                    snackbarHostState.showSnackbar(exportType.failedSnackbarMessage)
                },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            QuotiSnackbarHost(
                hostState = snackbarHostState,
                avoidToolbar = post != null,
            )
        },
    ) { innerPadding ->
        QuotiCaptureScreen(
            shareState = shareState,
            settings = settings,
            isExportProcessing = isExportProcessing,
            activeExportType = activeExportType,
            activeExportProgress = activeExportProgress,
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
                val activePost = post ?: return@QuotiCaptureScreen
                startExport(activePost, QuotiExportType.Video, CardContentMode.WithMedia)
            },
            onDownloadPng = {
                val activePost = post ?: return@QuotiCaptureScreen
                startExport(activePost, QuotiExportType.Image, settings.contentMode)
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
                    }.fold(
                        onSuccess = {
                            snackbarHostState.showSnackbar("Share sheet ready")
                        },
                        onFailure = {
                            snackbarHostState.showSnackbar("Unable to share image")
                        },
                    )
                }
            },
            onCopyText = {
                val activePost = post ?: return@QuotiCaptureScreen
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "Quoti post text",
                        activePost.content,
                    ),
                )
                scope.launch {
                    snackbarHostState.showSnackbar("Text copied")
                }
            },
            onCopySource = {
                val sourceUrl = post?.sourceUrl ?: return@QuotiCaptureScreen
                clipboard.setPrimaryClip(ClipData.newPlainText("Quoti source link", sourceUrl))
                scope.launch {
                    snackbarHostState.showSnackbar("Source link copied")
                }
            },
            onOpenSource = {
                val sourceUrl = post?.sourceUrl ?: return@QuotiCaptureScreen
                uriHandler.openUri(sourceUrl)
            },
            onClear = {
                onClear()
                scope.launch {
                    snackbarHostState.showSnackbar("Post cleared")
                }
            },
            onRefresh = {
                scope.launch {
                    snackbarHostState.showSnackbar("Refresh will reprocess the next shared post")
                }
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
private fun QuotiSnackbarHost(
    hostState: SnackbarHostState,
    avoidToolbar: Boolean,
) {
    SnackbarHost(
        hostState = hostState,
        modifier =
            Modifier
                .navigationBarsPadding()
                .padding(bottom = if (avoidToolbar) 98.dp else 0.dp),
    ) { snackbarData ->
        Snackbar(
            snackbarData = snackbarData,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuotiCaptureScreen(
    shareState: QuotiShareState,
    settings: QuotiUiSettings,
    isExportProcessing: Boolean,
    activeExportType: QuotiExportType,
    activeExportProgress: Int,
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
    onClear: () -> Unit,
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
                onClear = onClear,
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

        if (isExportProcessing) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                contentAlignment = Alignment.Center,
            ) {
                ExportProcessingState(
                    exportType = activeExportType,
                    progressPercent = activeExportProgress,
                    modifier =
                        Modifier
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                )
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
        MaterialLoadingIndicator(
            modifier = Modifier.size(48.dp),
            contentDescription = "Preparing post",
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Preparing post",
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}

@Composable
private fun ExportProcessingState(
    exportType: QuotiExportType,
    progressPercent: Int,
    modifier: Modifier = Modifier,
) {
    StateFrame(modifier = modifier) {
        MaterialLoadingIndicator(
            modifier = Modifier.size(48.dp),
            contentDescription = exportType.processingTitle,
            contained = true,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = exportType.processingTitle,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MaterialLinearProgressIndicator(
            progressPercent = progressPercent,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(18.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$progressPercent%",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can leave Quoti or lock your phone. We'll notify you when it's ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MaterialLoadingIndicator(
    modifier: Modifier = Modifier,
    contentDescription: String,
    contained: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val materialContext =
                ContextThemeWrapper(context, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            val indicatorContext =
                if (contained) {
                    ContextThemeWrapper(materialContext, com.quoti.android.R.style.Quoti_LoadingIndicatorContained)
                } else {
                    materialContext
                }
            LoadingIndicator(indicatorContext).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                this.contentDescription = contentDescription
            }
        },
    )
}

@Composable
private fun MaterialLinearProgressIndicator(
    progressPercent: Int,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val materialContext =
                ContextThemeWrapper(context, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            val indicatorContext =
                ContextThemeWrapper(materialContext, com.quoti.android.R.style.Quoti_LinearProgressIndicatorWavy)
            LinearProgressIndicator(indicatorContext).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                max = 100
                isIndeterminate = false
                progress = progressPercent.coerceIn(0, 100)
            }
        },
        update = { indicator ->
            indicator.setProgressCompat(progressPercent.coerceIn(0, 100), true)
        },
    )
}

private suspend fun SnackbarHostState.showSavedMediaSnackbar(
    context: Context,
    message: String,
    uri: Uri,
    mimeType: String,
    failureMessage: String,
) {
    val result =
        showSnackbar(
            message = message,
            actionLabel = "Voir",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )

    if (result == SnackbarResult.ActionPerformed) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            showSnackbar(failureMessage)
        }
    }
}

@Composable
private fun StateFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
        value = emptyList()
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
    var videoMuted by remember(media.playableVideoUrl) { mutableStateOf(true) }
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
                muted = videoMuted,
                modifier = Modifier.fillMaxSize(),
            )
            VideoSoundButton(
                muted = videoMuted,
                onClick = { videoMuted = !videoMuted },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
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
private fun VideoSoundButton(
    muted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescription = if (muted) "Enable sound" else "Mute video"
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun VideoPlayer(
    videoUrl: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                tag = videoUrl
                surfaceTextureListener = QuotiTextureVideoListener(videoUrl, muted)
            }
        },
        update = { view ->
            if (view.tag != videoUrl) {
                view.tag = videoUrl
                (view.surfaceTextureListener as? QuotiTextureVideoListener)?.release()
                view.surfaceTextureListener = QuotiTextureVideoListener(videoUrl, muted)
                if (view.isAvailable) {
                    view.surfaceTexture?.let { texture ->
                        (view.surfaceTextureListener as? QuotiTextureVideoListener)
                            ?.start(texture)
                    }
                }
            } else {
                (view.surfaceTextureListener as? QuotiTextureVideoListener)?.setMuted(muted)
            }
        },
    )
}

private class QuotiTextureVideoListener(
    private val videoUrl: String,
    private var muted: Boolean,
) : TextureView.SurfaceTextureListener {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        start(surfaceTexture)
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) = Unit

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    fun start(surfaceTexture: SurfaceTexture) {
        release()
        val nextSurface = Surface(surfaceTexture)
        surface = nextSurface
        mediaPlayer =
            MediaPlayer().apply {
                setDataSource(videoUrl)
                setSurface(nextSurface)
                isLooping = true
                applyVolume()
                setOnPreparedListener { player ->
                    player.applyVolume()
                    player.start()
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        mediaPlayer?.applyVolume()
    }

    fun release() {
        mediaPlayer?.runCatchingRelease()
        mediaPlayer = null
        surface?.release()
        surface = null
    }

    private fun MediaPlayer.applyVolume() {
        val volume = if (muted) 0f else 1f
        setVolume(volume, volume)
    }
}

private fun MediaPlayer.runCatchingRelease() {
    runCatching {
        stop()
    }
    release()
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
    onClear: () -> Unit,
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
            if (hasVideo) {
                ToolbarActionButton(
                    label = "Copy image",
                    onClick = onCopyImage,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "Copy image",
                    )
                }
            } else {
                ToolbarActionButton(
                    label = "Download PNG",
                    onClick = onDownloadPng,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download PNG",
                    )
                }
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
                    if (hasVideo) {
                        DropdownMenuItem(
                            text = { Text("Download PNG") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onDownloadPng()
                            },
                        )
                    }
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
                        text = { Text("Clear post") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            overflowExpanded = false
                            onClear()
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
    Icon(
        imageVector = if (hasVideo) Icons.Outlined.Download else Icons.Outlined.ContentCopy,
        contentDescription = contentDescription,
        modifier = Modifier.size(28.dp),
    )
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

private fun QuotiPost.containsVideo(): Boolean {
    return media.any { it is PostMedia.Video } ||
        relatedPost?.media?.any { it is PostMedia.Video } == true
}

private val QuotiExportType.processingTitle: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Saving image"
            QuotiExportType.Video -> "Processing video"
        }

private val QuotiExportType.startedSnackbarMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Image export started"
            QuotiExportType.Video -> "Video export started"
        }

private val QuotiExportType.readySnackbarMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Image ready"
            QuotiExportType.Video -> "Video ready"
        }

private val QuotiExportType.savedSnackbarMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "PNG saved to Pictures/Quoti"
            QuotiExportType.Video -> "Video saved to Movies/Quoti"
        }

private val QuotiExportType.openFailureSnackbarMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Unable to open image"
            QuotiExportType.Video -> "Unable to open video"
        }

private val QuotiExportType.failedSnackbarMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Unable to save PNG"
            QuotiExportType.Video -> "Unable to export video"
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
