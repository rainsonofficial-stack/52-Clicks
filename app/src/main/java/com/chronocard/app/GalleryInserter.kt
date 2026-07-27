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

        val jitterMillis = Random.nextLong(-20 * 60 * 1000L, 20 * 60 * 1000L)
        var target = now - baseOffsetMillis + jitterMillis
        if (target >= now) target = now - 60_000
        return target
    }

    /**
     * Copies the given source image into DCIM/Camera with DATE_TAKEN /
     * DATE_ADDED / DATE_MODIFIED all set to targetMillis.
     *
     * If the source photo actually came from the phone's camera, it carries
     * a large/nonstandard EXIF block (maker notes, embedded thumbnail) that
     * can make ExifInterface.saveAttributes() throw on some Samsung images.
     * If that write silently fails, Android's rescan falls back to the
     * ORIGINAL embedded DateTimeOriginal, and the backdate never takes
     * effect. To avoid this entirely we re-encode the copy as a clean JPEG
     * (Bitmap decode + recompress) before writing our own minimal EXIF date
     * tags, so there's no legacy maker-note data to trip over. We preserve
     * the source's rotation by copying its ORIENTATION tag across.
     */
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

        // Read the original rotation before we strip its EXIF away.
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

        // Clean file, no legacy maker-note baggage - this write is now reliable.
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

    /** Decodes with downsampling for very large camera photos, to keep this fast. */
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
            // Best-effort — the MediaStore DATE_TAKEN column is still set as a fallback.
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
