package com.remotecamera.viewer.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.remotecamera.viewer.debug.DebugLogger
import org.webrtc.VideoFrame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FrameSaverHelper {

    private val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /**
     * Converts a WebRTC I420Buffer to Android Bitmap.
     */
    fun i420ToBitmap(i420Buffer: VideoFrame.I420Buffer): Bitmap? {
        return try {
            val width = i420Buffer.width
            val height = i420Buffer.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val yData = i420Buffer.dataY
            val uData = i420Buffer.dataU
            val vData = i420Buffer.dataV

            val yStride = i420Buffer.strideY
            val uStride = i420Buffer.strideU
            val vStride = i420Buffer.strideV

            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val yOffset = y * yStride
                val uvRow = y / 2
                val uOffset = uvRow * uStride
                val vOffset = uvRow * vStride
                for (x in 0 until width) {
                    val uCol = x / 2
                    val yVal = (yData.get(yOffset + x).toInt() and 0xFF)
                    val uVal = (uData.get(uOffset + uCol).toInt() and 0xFF) - 128
                    val vVal = (vData.get(vOffset + uCol).toInt() and 0xFF) - 128

                    var r = (yVal + 1.370705f * vVal).toInt()
                    var g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt()
                    var b = (yVal + 1.732446f * uVal).toInt()

                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)

                    pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            DebugLogger.log("FrameSaver", "Error converting frame to bitmap: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
            null
        }
    }

    /**
     * Saves a Bitmap to Pictures/RemoteCamera_[cameraName]/ folder on the Viewer device.
     */
    fun savePhotoToStorage(context: Context, cameraName: String, bitmap: Bitmap): Uri? {
        val sanitizedCameraName = cameraName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val folderName = "RemoteCamera_$sanitizedCameraName"
        val fileName = "IMG_${timeFormat.format(Date())}.jpg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folderName")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                }
                uri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(picturesDir, folderName)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                val file = File(folder, fileName)
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            DebugLogger.log("FrameSaver", "Failed to save photo to disk: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
            null
        }
    }
}
