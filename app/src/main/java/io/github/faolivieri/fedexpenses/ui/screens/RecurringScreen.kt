package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.Frequency
import io.github.faolivieri.fedexpenses.data.Recurrence
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.CategoryBadge
import io.github.faolivieri.fedexpenses.ui.components.EmptyState
import io.github.faolivieri.fedexpenses.ui.components.SignedAmount
import io.github.faolivieri.fedexpenses.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate

val Frequency.label: String
    get() = when (this) {
        Frequency.WEEKLY -> "Weekly"
        Frequency.MONTHLY -> "Monthly"
        Frequency.YEARLY -> "Yearly"
    }

/** Every repeating series, soonest first; tap to edit its latest payment, ✕ to stop it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val series by vm.recurrences.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val byId = remember(categories) { categories.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    var stopping by remember { mutableStateOf<Recurrence?>(null) }
    var notStarted by remember { mutableStateOf(false) }

    // Active ones by next date, then the ones that have ended.
    val sorted = remember(series) { series.sortedWith(compareBy(nullsLast()) { it.nextDate }) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Repeating payments") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (sorted.isEmpty()) item {
                EmptyState("No repeating payments yet. When adding or editing a transaction, set Repeat to weekly, monthly or yearly.")
            }
            items(sorted, key = { it.id }) { r ->
                val category = byId[r.categoryId]
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val latest = vm.latestIdInSeries(r.id)
                                if (latest != null) onOpen(latest) else { notStarted = true; stopping = r }
                            }
                        }
                        .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (category != null) CategoryBadge(category)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.merchant?.takeIf { it.isNotBlank() } ?: r.note.takeIf { it.isNotBlank() } ?: category?.name.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(schedule(r), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SignedAmount(r.amountCents, r.type)
                    IconButton(onClick = { notStarted = false; stopping = r }) {
                        Icon(Icons.Filled.Close, "Stop repeating", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    stopping?.let { r ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            title = { Text("Stop repeating?") },
            text = {
                Text(
                    (if (notStarted) "This series hasn't added a payment yet (the first is due ${Dates.short(r.start)}). " else "") +
                        "Payments already added stay; no new ones will be created."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.stopRecurring(r.id); stopping = null }) {
                    Text("Stop repeating", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { stopping = null }) { Text("Cancel") } },
        )
    }
}

private fun schedule(r: Recurrence): String {
    val next = r.nextDate
    val until = r.end?.let { " · until ${Dates.short(it)}" }.orEmpty()
    return when {
        next == null -> "${r.frequency.label} · ended"
        next == LocalDate.now() -> "${r.frequency.label} · next today$until"
        else -> "${r.frequency.label} · next ${Dates.short(next)}$until"
    }
}
