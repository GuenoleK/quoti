package com.quoti.android.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PngMimeType = "image/png"
private const val ExportDirectory = "quoti_exports"
private const val PicturesRelativePath = "Pictures/Quoti"

object QuotiCardExporter {
    suspend fun writeCachePng(
        context: Context,
        rootView: View,
        cardBounds: Rect,
        postId: String,
    ): Uri {
        val bitmap = captureBitmap(rootView, cardBounds)
        val fileName = quotiCardFileName(postId)

        return withContext(Dispatchers.IO) {
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
    }

    suspend fun writePicturesPng(
        context: Context,
        rootView: View,
        cardBounds: Rect,
        postId: String,
    ): Uri {
        val bitmap = captureBitmap(rootView, cardBounds)
        val fileName = quotiCardFileName(postId)

        return withContext(Dispatchers.IO) {
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
    }

    private fun captureBitmap(
        rootView: View,
        cardBounds: Rect,
    ): Bitmap {
        check(rootView.width > 0 && rootView.height > 0) {
            "Quoti card root view has not been laid out."
        }

        val safeBounds = Rect()
        check(safeBounds.setIntersect(cardBounds, Rect(0, 0, rootView.width, rootView.height))) {
            "Quoti card is not visible yet."
        }
        check(safeBounds.width() > 0 && safeBounds.height() > 0) {
            "Quoti card is not visible yet."
        }

        val rootBitmap =
            Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        rootView.draw(Canvas(rootBitmap))

        return Bitmap.createBitmap(
            rootBitmap,
            safeBounds.left,
            safeBounds.top,
            safeBounds.width(),
            safeBounds.height(),
        )
    }
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
