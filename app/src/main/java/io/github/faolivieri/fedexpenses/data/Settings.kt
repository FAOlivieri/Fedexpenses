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

    /** Emits whenever any setting changes, so screens can refresh. */
    fun changes(): Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
}
