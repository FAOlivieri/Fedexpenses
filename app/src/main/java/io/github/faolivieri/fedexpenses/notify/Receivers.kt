package io.github.faolivieri.fedexpenses.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.faolivieri.fedexpenses.container
import io.github.faolivieri.fedexpenses.work.RecurringAlarm
import kotlinx.coroutines.launch

/** Handles the category quick-pick buttons on "What category is this?" notifications. */
class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_CATEGORY) return
        val txId = intent.getLongExtra(EXTRA_TX_ID, 0)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, 0)
        if (txId == 0L || categoryId == 0L) return
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                val tx = c.repo.setCategory(txId, categoryId)
                val cat = c.repo.getCategory(categoryId)
                if (tx != null && cat != null) c.notifier.showCategorized(tx, cat) else c.notifier.cancel(txId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_CATEGORY = "io.github.faolivieri.fedexpenses.SET_CATEGORY"
        const val EXTRA_TX_ID = "tx_id"
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}

/**
 * After a reboot, an app update or a clock change: rebind the listener and re-arm the nightly
 * alarm for repeating transactions (the app's startup also catches up on any that are due).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PaymentListenerService.ensureBound(context)
        RecurringAlarm.schedule(context)
    }
}
