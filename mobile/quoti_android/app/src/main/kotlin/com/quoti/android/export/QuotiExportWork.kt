package com.quoti.android.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.quoti.android.R
import com.quoti.android.core.model.CardContentMode
import com.quoti.android.core.model.CardTone
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.quotiPostFromJsonString
import com.quoti.android.core.model.toJsonString
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ExportLogTag = "QuotiExport"

enum class QuotiExportType {
    Image,
    Video,
}

object QuotiExportWork {
    const val ProgressPercent = "progress_percent"
    const val OutputUri = "output_uri"
    const val OutputMimeType = "output_mime_type"
    const val OutputMessage = "output_message"
    const val OutputFailureMessage = "output_failure_message"

    suspend fun enqueue(
        context: Context,
        post: QuotiPost,
        exportType: QuotiExportType,
        cardTone: CardTone,
        contentMode: CardContentMode,
        selectedVideoSourceId: String? = null,
    ): UUID =
        withContext(Dispatchers.IO) {
            val applicationContext = context.applicationContext
            val jobId = UUID.randomUUID().toString()
            val jsonFile = File(applicationContext.cacheDir, ExportJobDirectory)
                .also(File::mkdirs)
                .resolve("$jobId.json")
            jsonFile.writeText(post.toJsonString())
            val inputData =
                mutableListOf<Pair<String, Any?>>(
                    InputJobId to jobId,
                    InputPostJsonPath to jsonFile.absolutePath,
                    InputExportType to exportType.name,
                    InputCardTone to cardTone.name,
                    InputContentMode to contentMode.name,
                    InputNotificationId to stableNotificationId(jobId),
                ).apply {
                    if (selectedVideoSourceId != null) {
                        add(InputSelectedVideoSourceId to selectedVideoSourceId)
                    }
                }

            val request =
                OneTimeWorkRequestBuilder<QuotiExportWorker>()
                    .setInputData(
                        workDataOf(*inputData.toTypedArray()),
                    )
                    .addTag(ExportWorkTag)
                    .build()

            WorkManager
                .getInstance(applicationContext)
                .enqueueUniqueWork(
                    "$UniqueExportWorkName-$jobId",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )

            request.id
        }

    fun isFinishedStateName(stateName: String): Boolean =
        stateName == "SUCCEEDED" || stateName == "FAILED" || stateName == "CANCELLED"
}

class QuotiExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val notifications = QuotiExportNotifications(applicationContext)
        val exportType = inputData.getString(InputExportType)?.let(QuotiExportType::valueOf) ?: return Result.failure()
        val cardTone = inputData.getString(InputCardTone)?.let(CardTone::valueOf) ?: return Result.failure()
        val contentMode = inputData.getString(InputContentMode)?.let(CardContentMode::valueOf) ?: return Result.failure()
        val jsonPath = inputData.getString(InputPostJsonPath) ?: return Result.failure()
        val jsonFile = File(jsonPath)
        val notificationId = inputData.getInt(InputNotificationId, stableNotificationId(id.toString()))
        val selectedVideoSourceId = inputData.getString(InputSelectedVideoSourceId)
        val outputMimeType = exportType.outputMimeType

        notifications.ensureChannel()
        setForeground(notifications.foregroundInfo(exportType, notificationId, id))
        setProgressPercent(0)

        return try {
            val post = withContext(Dispatchers.IO) {
                quotiPostFromJsonString(jsonFile.readText())
            }
            val outputUri =
                when (exportType) {
                    QuotiExportType.Image -> {
                        setProgressPercent(35)
                        QuotiCardExporter.writePicturesPng(
                            context = applicationContext,
                            post = post,
                            cardTone = cardTone,
                            contentMode = contentMode,
                        )
                    }

                    QuotiExportType.Video ->
                        QuotiCardExporter.writeMoviesMp4(
                            context = applicationContext,
                            post = post,
                            cardTone = cardTone,
                            contentMode = CardContentMode.WithMedia,
                            selectedVideoSourceId = selectedVideoSourceId,
                            onProgress = ::setProgressPercent,
                        )
                }

            setProgressPercent(100)
            notifications.showReady(exportType, outputUri, outputMimeType, notificationId + 1)
            Result.success(
                workDataOf(
                    QuotiExportWork.OutputUri to outputUri.toString(),
                    QuotiExportWork.OutputMimeType to outputMimeType,
                    QuotiExportWork.OutputMessage to exportType.savedMessage,
                    QuotiExportWork.OutputFailureMessage to exportType.openFailureMessage,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(ExportLogTag, "${exportType.name} export failed for work $id", throwable)
            notifications.showFailed(exportType, notificationId + 2)
            Result.failure(
                workDataOf(
                    QuotiExportWork.OutputFailureMessage to exportType.exportFailureMessage,
                ),
            )
        } finally {
            jsonFile.delete()
        }
    }

    private fun setProgressPercent(progressPercent: Int) {
        setProgressAsync(
            workDataOf(
                QuotiExportWork.ProgressPercent to progressPercent.coerceIn(0, 100),
            ),
        )
    }
}

private class QuotiExportNotifications(
    private val context: Context,
) {
    private val notificationManager: NotificationManager?
        get() = context.getSystemService()

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel =
            NotificationChannel(
                ExportChannelId,
                "Quoti exports",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Export progress and saved media alerts."
                setSound(null, AudioAttributes.Builder().build())
            }
        notificationManager?.createNotificationChannel(channel)
    }

    fun foregroundInfo(
        exportType: QuotiExportType,
        notificationId: Int,
        workId: UUID,
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val notification =
            baseBuilder()
                .setContentTitle(exportType.processingTitle)
                .setContentText(exportType.processingText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(exportType.processingText))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .addAction(R.drawable.ic_notification_quoti, "Stop", cancelIntent)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun showReady(
        exportType: QuotiExportType,
        uri: Uri,
        mimeType: String,
        notificationId: Int,
    ) {
        val viewIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            baseBuilder()
                .setContentTitle(exportType.readyTitle)
                .setContentText(exportType.readyText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(exportType.readyText))
                .setContentIntent(viewIntent)
                .addAction(R.drawable.ic_notification_quoti, "Voir", viewIntent)
                .setAutoCancel(true)
                .build()

        notificationManager?.notify(notificationId, notification)
    }

    fun showFailed(
        exportType: QuotiExportType,
        notificationId: Int,
    ) {
        notificationManager?.notify(
            notificationId,
            baseBuilder()
                .setContentTitle(exportType.failedTitle)
                .setContentText(exportType.failedText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(exportType.failedText))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, ExportChannelId)
            .setSmallIcon(R.drawable.ic_notification_quoti)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_STATUS)
}

private val QuotiExportType.outputMimeType: String
    get() =
        when (this) {
            QuotiExportType.Image -> "image/png"
            QuotiExportType.Video -> "video/mp4"
        }

private val QuotiExportType.processingTitle: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Preparing image"
            QuotiExportType.Video -> "Processing video"
        }

private val QuotiExportType.processingText: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Quoti is saving your image in the background."
            QuotiExportType.Video -> "Quoti is rendering your video in the background."
        }

private val QuotiExportType.readyTitle: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Image ready"
            QuotiExportType.Video -> "Video ready"
        }

private val QuotiExportType.readyText: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Your Quoti image has been saved."
            QuotiExportType.Video -> "Your Quoti video has been saved."
        }

private val QuotiExportType.failedTitle: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Image export failed"
            QuotiExportType.Video -> "Video export failed"
        }

private val QuotiExportType.failedText: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Quoti could not save this image."
            QuotiExportType.Video -> "Quoti could not render this video."
        }

private val QuotiExportType.savedMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "PNG saved to Pictures/Quoti"
            QuotiExportType.Video -> "Video saved to Movies/Quoti"
        }

private val QuotiExportType.openFailureMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Unable to open image"
            QuotiExportType.Video -> "Unable to open video"
        }

private val QuotiExportType.exportFailureMessage: String
    get() =
        when (this) {
            QuotiExportType.Image -> "Unable to save PNG"
            QuotiExportType.Video -> "Unable to export video"
        }

private fun stableNotificationId(value: String): Int = value.hashCode() and Int.MAX_VALUE

private const val ExportChannelId = "quoti_exports"
private const val ExportJobDirectory = "quoti_export_jobs"
private const val ExportWorkTag = "quoti_export"
private const val UniqueExportWorkName = "quoti_export"
private const val InputJobId = "job_id"
private const val InputPostJsonPath = "post_json_path"
private const val InputExportType = "export_type"
private const val InputCardTone = "card_tone"
private const val InputContentMode = "content_mode"
private const val InputNotificationId = "notification_id"
private const val InputSelectedVideoSourceId = "selected_video_source_id"
