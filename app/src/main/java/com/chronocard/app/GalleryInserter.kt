package com.chronocard.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object GalleryInserter {

    fun resolveTargetMillis(ctx: Context): Long {
        val now = System.currentTimeMillis()
        val option = SetupPrefs.getBackdate(ctx)

        if (option == SetupPrefs.Backdate.CUSTOM) {
            val custom = SetupPrefs.getCustomMillis(ctx)
            return if (custom in 1..now) custom else now
        }

        val baseOffsetMillis = when (option) {
            SetupPrefs.Backdate.H3 -> 3L * 60 * 60 * 1000
            SetupPrefs.Backdate.H10 -> 10L * 60 * 60 * 1000
            SetupPrefs.Backdate.H24 -> 24L * 60 * 60 * 1000
            SetupPrefs.Backdate.D3 -> 3L * 24 * 60 * 60 * 1000
            SetupPrefs.Backdate.CUSTOM -> 0L
        }

        // Extra hour-level variation on top of the minute jitter, but only for the
        // larger presets (24h / 3 days) — a couple hours of drift there still reads
        // naturally as "yesterday" / "a few days ago", whereas doing the same to the
        // 3h/10h presets would undercut what the performer explicitly picked.
        val extraHourJitter = if (option == SetupPrefs.Backdate.H24 || option == SetupPrefs.Backdate.D3) {
            val hours = Random.nextInt(2, 5) // 2, 3, or 4 hours
            val sign = if (Random.nextBoolean()) 1 else -1
            sign * hours * 60 * 60 * 1000L
        } else 0L

        val jitterMillis = Random.nextLong(-20 * 60 * 1000L, 20 * 60 * 1000L)
        var target = now - baseOffsetMillis + jitterMillis + extraHourJitter
        if (target >= now) target = now - 60_000
        return target
    }

    fun insertBackdatedImage(ctx: Context, sourcePath: String, targetMillis: Long): Boolean {
        val src = File(sourcePath)
        if (!src.exists()) return false

        val displayName = "IMG_${targetMillis}.jpg"
        val resolver = ctx.contentResolver

        val initialValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, targetMillis)
            put(MediaStore.Images.Media.DATE_ADDED, targetMillis / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, targetMillis / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, initialValues) ?: return false

        val originalOrientation = try {
            ExifInterface(sourcePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val wroteCleanCopy = try {
            val bitmap = decodeBitmap(sourcePath) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: return false
            bitmap.recycle()
            true
        } catch (e: Exception) {
            false
        }
        if (!wroteCleanCopy) return false

        writeExifDate(ctx, uri, targetMillis, originalOrientation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val finalValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
                put(MediaStore.Images.Media.DATE_TAKEN, targetMillis)
                put(MediaStore.Images.Media.DATE_ADDED, targetMillis / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, targetMillis / 1000)
            }
            resolver.update(uri, finalValues, null, null)
        }

        return true
    }

    private fun decodeBitmap(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxDimension = 2400
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun writeExifDate(ctx: Context, uri: Uri, millis: Long, orientation: Int) {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(millis))
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                exif.saveAttributes()
            }
        } catch (_: Exception) {
        }
    }

    fun sourcePathForCard(ctx: Context, card: CardUtils.Card): String? {
        val direct = SetupPrefs.getImagePathFor(ctx, card.key())
        if (direct != null && File(direct).exists()) return direct
        val fallback = SetupPrefs.getImagePathFor(ctx, CardUtils.FALLBACK_KEY)
        return if (fallback != null && File(fallback).exists()) fallback else null
    }

    fun performInsertForCard(ctx: Context, card: CardUtils.Card): Boolean {
        val sourcePath = sourcePathForCard(ctx, card) ?: return false
        val target = resolveTargetMillis(ctx)
        return insertBackdatedImage(ctx, sourcePath, target)
    }
}
