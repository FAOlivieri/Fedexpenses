package io.github.faolivieri.fedexpenses

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import io.github.faolivieri.fedexpenses.ui.ShareActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.faolivieri.fedexpenses.notify.PaymentListenerService
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.ExpensesNavHost
import io.github.faolivieri.fedexpenses.ui.Routes
import io.github.faolivieri.fedexpenses.ui.theme.ExpensesTheme
import io.github.faolivieri.fedexpenses.ui.theme.ThemeChoice
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import io.github.faolivieri.fedexpenses.work.Jobs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Editor route to open, set when launched from one of our notifications. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        Jobs.schedule(this)
        PaymentListenerService.ensureBound(this)
        val settings = container.settings
        setContent {
            // Re-themes live when the palette is changed in Settings.
            fun choice() = ThemeChoice(settings.themePalette, settings.themeCustomColor,
                settings.themeBackground, settings.themeBackgroundColor)
            val theme by remember { settings.changes().map { choice() } }.collectAsState(initial = choice())
            ExpensesTheme(theme) {
                ExpensesNavHost(pendingRoute = pendingRoute, onRouteConsumed = { pendingRoute.value = null })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The app may have stayed in memory past midnight.
        container.appScope.launch { container.repo.materializeRecurring() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_EDIT_TX -> {
                val id = intent.getLongExtra(EXTRA_TX_ID, 0)
                if (id != 0L) pendingRoute.value = Routes.edit(id)
            }
            ACTION_NEW_TX -> pendingRoute.value = Routes.edit(
                type = TxType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: TxType.EXPENSE.name),
                timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0),
                accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, 0),
            )
            else -> if (intent != null) lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { ShareActions.fromIntent(this@MainActivity, intent) }
                    ?: return@launch
                result.onSuccess {
                    container.incomingShare.value = it
                    pendingRoute.value = Routes.RECEIVE
                }.onFailure {
                    Toast.makeText(this@MainActivity, it.message ?: "Not a Fedexpenses share", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val ACTION_EDIT_TX = "io.github.faolivieri.fedexpenses.EDIT_TX"
        const val EXTRA_TX_ID = "tx_id"
        const val ACTION_NEW_TX = "io.github.faolivieri.fedexpenses.NEW_TX"
        const val EXTRA_TYPE = "type"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_ACCOUNT_ID = "account_id"
    }
}
