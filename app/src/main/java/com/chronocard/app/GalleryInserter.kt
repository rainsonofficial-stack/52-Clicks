package com.chronocard.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import kotlin.random.Random

object GalleryInserter {

    /**
     * Computes the target timestamp (millis) for the given backdate option.
     * All presets except CUSTOM get a small random jitter (minutes/seconds)
     * so repeated performances don't land on identical, suspiciously-round times.
     * The jitter never pushes the result into the future.
     */
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

        // Random variation: up to +/-20 minutes, applied as extra time further in the past
        // or slightly less far in the past, but always still before "now".
        val jitterMillis = Random.nextLong(-20 * 60 * 1000L, 20 * 60 * 1000L)
        var target = now - baseOffsetMillis + jitterMillis
        if (target >= now) target = now - 60_000 // safety: at least 1 min in the past
        return target
    }

    /**
     * Copies the given source image into the shared Pictures gallery with
     * DATE_TAKEN / DATE_ADDED / DATE_MODIFIED all set to targetMillis, so it
     * sorts into place as if it had been taken at that moment.
     */
    fun insertBackdatedImage(ctx: Context, sourcePath: String, targetMillis: Long): Boolean {
        val src = File(sourcePath)
        if (!src.exists()) return false

        val displayName = "IMG_${targetMillis}.jpg"
        val resolver = ctx.contentResolver

        val values = ContentValues().apply {
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
        val uri = resolver.insert(collection, values) ?: return false

        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(src).use { input -> input.copyTo(out) }
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return true
    }

    /** Resolves which source file to use for a card, falling back to Ace of Spades. */
    fun sourcePathForCard(ctx: Context, card: CardUtils.Card): String? {
        val direct = SetupPrefs.getImagePathFor(ctx, card.key())
        if (direct != null && File(direct).exists()) return direct
        val fallback = SetupPrefs.getImagePathFor(ctx, CardUtils.FALLBACK_KEY)
        return if (fallback != null && File(fallback).exists()) fallback else null
    }

    /**
     * One-shot version used by flows that unlock directly (passcode mode) with no
     * intermediate lock screen: resolves the photo, backdates it into the gallery,
     * and returns whether it succeeded.
     */
    fun performInsertForCard(ctx: Context, card: CardUtils.Card): Boolean {
        val sourcePath = sourcePathForCard(ctx, card) ?: return false
        val target = resolveTargetMillis(ctx)
        return insertBackdatedImage(ctx, sourcePath, target)
    }
}
