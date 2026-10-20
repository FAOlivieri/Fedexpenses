package io.github.faolivieri.fedexpenses

import android.app.Application
import android.content.Context
import io.github.faolivieri.fedexpenses.data.AppDatabase
import io.github.faolivieri.fedexpenses.data.Repository
import io.github.faolivieri.fedexpenses.data.Settings
import io.github.faolivieri.fedexpenses.notify.NotificationProcessor
import io.github.faolivieri.fedexpenses.notify.TxNotifier
import io.github.faolivieri.fedexpenses.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.faolivieri.fedexpenses.data.SharePayload
import io.github.faolivieri.fedexpenses.work.RecurringAlarm

/**
 * Everything is created lazily: when only the notification listener wakes the process,
 * no UI-side objects are built.
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)
    val db: AppDatabase by lazy { AppDatabase.build(context) }
    val repo: Repository by lazy { Repository(db, settings) }
    val notifier: TxNotifier by lazy { TxNotifier(context) }
    val processor: NotificationProcessor by lazy { NotificationProcessor(repo, notifier, appScope) }
    /** A share received from another phone, waiting to be reviewed. */
    val incomingShare = MutableStateFlow<SharePayload?>(null)
}

class ExpensesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Money.symbol = container.settings.currencySymbol
        // Whatever woke the process (UI, listener, alarm, boot), catch up on repeating transactions.
        container.appScope.launch { container.repo.materializeRecurring() }
        RecurringAlarm.schedule(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as ExpensesApp).container
