package com.carstensen.parcelcam.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {

    fun nowStamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    fun baseNameNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        return sdf.format(Date())
    }

    fun addTimestampWatermark(src: Bitmap, stamp: String): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (bmp.width.coerceAtMost(bmp.height) * 0.035f).coerceAtLeast(28f)
            setShadowLayer(6f, 2f, 2f, Color.BLACK)
        }

        val padding = (paint.textSize * 0.6f)
        val x = padding
        val y = bmp.height - padding

        canvas.drawText(stamp, x, y, paint)
        return bmp
    }

    fun resizeLongSide(src: Bitmap, longSidePx: Int?): Bitmap {
        if (longSidePx == null) return src
        val w = src.width
        val h = src.height
        val longSide = maxOf(w, h)
        if (longSide <= longSidePx) return src

        val scale = longSidePx.toFloat() / longSide.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    fun compressJpegToFile(bmp: Bitmap, quality: Int, file: File) {
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        }
    }

    fun saveToGallery(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val name = file.name

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ParcelCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
