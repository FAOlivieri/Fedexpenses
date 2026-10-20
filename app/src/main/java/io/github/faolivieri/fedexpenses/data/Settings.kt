package io.github.faolivieri.fedexpenses.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var currencySymbol: String
        get() = prefs.getString("currency", "€") ?: "€"
        set(v) = prefs.edit().putString("currency", v).apply()

    /** Persisted SAF document (e.g. a file in Google Drive) that automatic backups overwrite. */
    var backupUri: String?
        get() = prefs.getString("backup_uri", null)
        set(v) = prefs.edit().putString("backup_uri", v).apply()

    var lastBackupAt: Long
        get() = prefs.getLong("last_backup_at", 0)
        set(v) = prefs.edit().putLong("last_backup_at", v).apply()

    var lastBackupError: String?
        get() = prefs.getString("last_backup_error", null)
        set(v) = prefs.edit().putString("last_backup_error", v).apply()

    var autoBackup: Boolean
        get() = prefs.getBoolean("auto_backup", true)
        set(v) = prefs.edit().putBoolean("auto_backup", v).apply()

    /** App color palette: a preset id, "wallpaper" or "custom" (see ui.theme.Palettes). */
    var themePalette: String
        get() = prefs.getString("theme_palette", "ocean") ?: "ocean"
        set(v) = prefs.edit().putString("theme_palette", v).apply()

    /** Seed color for the "custom" palette. */
    var themeCustomColor: Int
        get() = prefs.getInt("theme_custom_color", 0xFF2A78D6.toInt())
        set(v) = prefs.edit().putInt("theme_custom_color", v).apply()

    /** "palette", "neutral" or "custom" (see ui.theme.Backgrounds). */
    var themeBackground: String
        get() = prefs.getString("theme_background", "palette") ?: "palette"
        set(v) = prefs.edit().putString("theme_background", v).apply()

    /** Color for the "custom" background. */
    var themeBackgroundColor: Int
        get() = prefs.getInt("theme_background_color", 0xFF8D6E63.toInt())
        set(v) = prefs.edit().putInt("theme_background_color", v).apply()

    /** This phone's identity when sharing expenses; created on first use. */
    val myId: String
        get() = prefs.getString("my_id", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("my_id", it).apply()
        }

    fun restoreMyId(id: String) = prefs.edit().putString("my_id", id).apply()

    /** The name other people see on what you share. */
    var myNickname: String
        get() = prefs.getString("my_nickname", "") ?: ""
        set(v) = prefs.edit().putString("my_nickname", v).apply()

    /** Emits whenever any setting changes, so screens can refresh. */
    fun changes(): Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
}
