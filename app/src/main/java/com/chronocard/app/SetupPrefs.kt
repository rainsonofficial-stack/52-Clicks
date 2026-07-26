package com.chronocard.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Everything configured on the setup screen, persisted so the perform flow
 * is a single tap from a cold app start.
 */
object SetupPrefs {
    private const val FILE = "chronocard_prefs"
    private const val KEY_IMAGE_MAP = "image_map"       // card key -> internal file path
    private const val KEY_BACKGROUND = "background_path"
    private const val KEY_MODE = "input_mode"            // "PASSCODE" | "TAP"
    private const val KEY_BACKDATE = "backdate_option"    // "H3" | "H10" | "H24" | "D3" | "CUSTOM"
    private const val KEY_CUSTOM_MILLIS = "custom_millis"

    enum class Mode { PASSCODE, TAP }
    enum class Backdate { H3, H10, H24, D3, CUSTOM }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setImagePath(ctx: Context, cardKey: String, path: String) {
        val map = getImageMap(ctx)
        map[cardKey] = path
        prefs(ctx).edit { putString(KEY_IMAGE_MAP, JSONObject(map as Map<*, *>).toString()) }
    }

    fun getImageMap(ctx: Context): MutableMap<String, String> {
        val raw = prefs(ctx).getString(KEY_IMAGE_MAP, null) ?: return mutableMapOf()
        val obj = JSONObject(raw)
        val out = mutableMapOf<String, String>()
        obj.keys().forEach { k -> out[k] = obj.getString(k) }
        return out
    }

    fun getImagePathFor(ctx: Context, cardKey: String): String? = getImageMap(ctx)[cardKey]

    fun setBackground(ctx: Context, path: String) =
        prefs(ctx).edit { putString(KEY_BACKGROUND, path) }

    fun getBackground(ctx: Context): String? = prefs(ctx).getString(KEY_BACKGROUND, null)

    fun setMode(ctx: Context, mode: Mode) =
        prefs(ctx).edit { putString(KEY_MODE, mode.name) }

    fun getMode(ctx: Context): Mode =
        Mode.valueOf(prefs(ctx).getString(KEY_MODE, Mode.PASSCODE.name)!!)

    fun setBackdate(ctx: Context, option: Backdate, customMillis: Long? = null) =
        prefs(ctx).edit {
            putString(KEY_BACKDATE, option.name)
            if (customMillis != null) putLong(KEY_CUSTOM_MILLIS, customMillis)
        }

    fun getBackdate(ctx: Context): Backdate =
        Backdate.valueOf(prefs(ctx).getString(KEY_BACKDATE, Backdate.H3.name)!!)

    fun getCustomMillis(ctx: Context): Long = prefs(ctx).getLong(KEY_CUSTOM_MILLIS, 0L)
}
