package io.github.faolivieri.fedexpenses.work

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.faolivieri.fedexpenses.container
import io.github.faolivieri.fedexpenses.notify.PaymentListenerService
import kotlinx.coroutines.delay
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** Hourly check that the notification listener is still bound; rebinds it if not. */
class ListenerWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // If this run started the process, give the system a moment to bind the listener itself.
        if (!PaymentListenerService.connected) delay(10_000)
        PaymentListenerService.ensureBound(applicationContext)
        return Result.success()
    }
}

/** Writes a full JSON backup to the user-chosen document (e.g. in Google Drive). */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val uri = c.settings.backupUri ?: return Result.success()
        return try {
            writeBackup(applicationContext, Uri.parse(uri))
            Result.success()
        } catch (e: Exception) {
            c.settings.lastBackupError = e.message ?: e.javaClass.simpleName
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        suspend fun writeBackup(context: Context, uri: Uri) {
            val c = context.container
            val json = c.repo.exportBackup()
            openForOverwrite(context, uri).use { it.write(json.toByteArray()) }
            c.settings.lastBackupAt = System.currentTimeMillis()
            c.settings.lastBackupError = null
        }
    }
}

/** Truncating write; falls back to plain "w" for providers (e.g. some cloud drives) that reject "wt". */
fun openForOverwrite(context: Context, uri: Uri): OutputStream {
    val resolver = context.contentResolver
    return try {
        resolver.openOutputStream(uri, "wt")
    } catch (e: Exception) {
        if (e is SecurityException) throw e
        resolver.openOutputStream(uri, "w")
    } ?: error("Can't open $uri for writing")
}

object Jobs {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "listener-watchdog", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(1, TimeUnit.HOURS).build(),
        )
        scheduleBackup(context)
    }

    fun scheduleBackup(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = context.container.settings
        if (s.backupUri != null && s.autoBackup) {
            wm.enqueueUniquePeriodicWork(
                "daily-backup", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        } else {
            wm.cancelUniqueWork("daily-backup")
        }
    }

    fun backupNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "backup-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().build(),
        )
    }
}
