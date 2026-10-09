package io.github.faolivieri.fedexpenses.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.faolivieri.fedexpenses.container
import io.github.faolivieri.fedexpenses.data.CapturedNotification
import io.github.faolivieri.fedexpenses.data.NotificationRule
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.ui.theme.Palettes
import io.github.faolivieri.fedexpenses.work.BackupWorker
import io.github.faolivieri.fedexpenses.work.Jobs
import io.github.faolivieri.fedexpenses.work.openForOverwrite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val currency: String,
    val backupUri: String?,
    val backupName: String?,
    val lastBackupAt: Long,
    val lastBackupError: String?,
    val autoBackup: Boolean,
    val palette: String,
    val customColor: Int,
)

data class AppEntry(val label: String, val packageName: String)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val repo = c.repo
    private val settings = c.settings
    private val resolver = app.contentResolver

    val rules: StateFlow<List<NotificationRule>> =
        repo.rules.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val captured: StateFlow<List<CapturedNotification>> =
        repo.notificationLog.observeCaptured().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<SettingsState?> = settings.changes().map {
        SettingsState(
            currency = settings.currencySymbol,
            backupUri = settings.backupUri,
            backupName = settings.backupUri?.let { displayName(Uri.parse(it)) },
            lastBackupAt = settings.lastBackupAt,
            lastBackupError = settings.lastBackupError,
            autoBackup = settings.autoBackup,
            palette = settings.themePalette,
            customColor = settings.themeCustomColor,
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** One-shot message for a dialog. */
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    private fun perform(block: suspend () -> String) {
        viewModelScope.launch {
            busy.value = true
            message.value = try {
                block()
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy.value = false
            }
        }
    }

    fun importCsv(uri: Uri) = perform {
        val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
        val s = repo.importCsv(text)
        buildString {
            append("Imported ${s.added} transactions")
            if (s.newCategories > 0) append(" and created ${s.newCategories} categories")
            append(".")
            if (s.skippedDuplicates > 0) append("\nSkipped ${s.skippedDuplicates} already in the app.")
            if (s.errors.isNotEmpty()) append("\n${s.errors.size} lines couldn't be read:\n" + s.errors.take(5).joinToString("\n"))
        }
    }

    fun exportCsv(uri: Uri) = perform {
        val csv = repo.exportCsv()
        withContext(Dispatchers.IO) { openForOverwrite(getApplication(), uri).use { it.write(csv.toByteArray()) } }
        "Exported ${csv.lines().size - 2} transactions."
    }

    fun exportBackup(uri: Uri) = perform {
        val json = repo.exportBackup()
        withContext(Dispatchers.IO) { openForOverwrite(getApplication(), uri).use { it.write(json.toByteArray()) } }
        "Backup saved."
    }

    fun restoreBackup(uri: Uri) = perform {
        val json = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
        val d = repo.restoreBackup(json)
        "Restored ${d.transactions.size} transactions, ${d.categories.size} categories and " +
            "${d.accounts.size} account${if (d.accounts.size == 1) "" else "s"}."
    }

    /** Remember a document (typically in Google Drive) and keep overwriting it with fresh backups. */
    fun chooseBackupFile(uri: Uri) = perform {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        settings.backupUri?.let { old ->
            if (old != uri.toString()) runCatching {
                resolver.releasePersistableUriPermission(Uri.parse(old),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        settings.backupUri = uri.toString()
        settings.autoBackup = true
        withContext(Dispatchers.IO) { BackupWorker.writeBackup(getApplication(), uri) }
        Jobs.scheduleBackup(getApplication())
        "Backup written. It will be refreshed daily."
    }

    fun backupNow() = perform {
        val uri = settings.backupUri ?: return@perform "Choose a backup file first."
        try {
            withContext(Dispatchers.IO) { BackupWorker.writeBackup(getApplication(), Uri.parse(uri)) }
        } catch (e: Exception) {
            settings.lastBackupError = e.message ?: e.javaClass.simpleName
            throw e
        }
        "Backup written."
    }

    fun setAutoBackup(on: Boolean) {
        settings.autoBackup = on
        Jobs.scheduleBackup(getApplication())
    }

    fun setPalette(id: String) { settings.themePalette = id }

    fun setCustomColor(argb: Int) {
        settings.themeCustomColor = argb
        settings.themePalette = Palettes.CUSTOM
    }

    fun setCurrency(symbol: String) {
        settings.currencySymbol = symbol
        Money.symbol = symbol
    }

    suspend fun getRule(id: Long): NotificationRule? = repo.rules.get(id)

    fun saveRule(rule: NotificationRule) = viewModelScope.launch {
        if (rule.id == 0L) repo.rules.insert(rule) else repo.rules.update(rule)
    }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.rules.delete(id) }

    fun processCaptured(capturedId: Long, rule: NotificationRule) = viewModelScope.launch {
        c.processor.processCaptured(capturedId, rule)
    }

    fun setRuleEnabled(rule: NotificationRule, enabled: Boolean) = viewModelScope.launch {
        repo.rules.update(rule.copy(enabled = enabled))
    }

    suspend fun launchableApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(packageName: String): String? = runCatching {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
}
