package io.github.faolivieri.fedexpenses.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.faolivieri.fedexpenses.container
import io.github.faolivieri.fedexpenses.util.toMillis
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Wakes the app once a day just after midnight to create that day's repeating transactions.
 * Inexact on purpose (no special permission, negligible battery); anything it misses is caught
 * up whenever the app or the notification listener starts.
 */
object RecurringAlarm {
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = LocalDate.now().plusDays(1).toMillis() + 60_000 // 00:01 tomorrow
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RecurringReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class RecurringReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                c.repo.materializeRecurring()
            } finally {
                RecurringAlarm.schedule(context)
                pending.finish()
            }
        }
    }
}
