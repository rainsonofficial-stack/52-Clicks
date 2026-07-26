package com.chronocard.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
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
     * IMPORTANT: on scoped storage, clearing IS_PENDING triggers a MediaStore
     * rescan that can silently reset DATE_MODIFIED (and, on Samsung/One UI,
     * the gallery sorts by EXIF DateTimeOriginal rather than the DB column).
     * So we (a) write real EXIF date tags into the file itself, and (b)
     * re-assert the date columns in the SAME update call that clears
     * IS_PENDING, so nothing overwrites them after the fact.
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

        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(src).use { input -> input.copyTo(out) }
            } ?: return false
        } catch (e: Exception) {
            return false
        }

        writeExifDate(ctx, uri, targetMillis)

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

    private fun writeExifDate(ctx: Context, uri: Uri, millis: Long) {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(millis))
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
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
