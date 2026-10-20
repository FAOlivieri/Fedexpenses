package io.github.faolivieri.fedexpenses.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.ImportChoice
import io.github.faolivieri.fedexpenses.data.Incoming
import io.github.faolivieri.fedexpenses.data.IncomingKind
import io.github.faolivieri.fedexpenses.data.Person
import io.github.faolivieri.fedexpenses.data.ShareItem
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.toLocalDate
import kotlinx.coroutines.launch

/** Reviews expenses another phone shared, then adds the chosen ones. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiveScreen(vm: MainViewModel, onDone: () -> Unit) {
    val payload by vm.incomingShare.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val p = payload ?: run { LaunchedEffect(Unit) { onDone() }; return }
    var incoming by remember(p) { mutableStateOf<List<Incoming>?>(null) }
    var sender by remember(p) { mutableStateOf<Person?>(null) }
    /** The person chosen as the sender; null adds them as a new person. */
    var senderChoice by remember(p) { mutableStateOf<Long?>(null) }
    val choices = remember(p) { mutableStateListOf<ImportChoice>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(p) {
        val list = vm.previewShare(p)
        sender = vm.senderOf(p)
        senderChoice = sender?.id
        choices.clear()
        choices.addAll(list.map {
            when (it.kind) {
                IncomingKind.ALREADY_ADDED -> ImportChoice.SKIP
                IncomingKind.POSSIBLE_DUPLICATE -> ImportChoice.LINK
                else -> ImportChoice.ADD
            }
        })
        incoming = list
    }

    fun close() {
        vm.incomingShare.value = null
        onDone()
    }
    BackHandler(onBack = ::close)

    val list = incoming
    val count = choices.count { it != ImportChoice.SKIP }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = ::close) { Icon(Icons.Filled.Close, "Close") } },
                title = { Text("From ${p.fromName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
        bottomBar = {
            Button(
                enabled = list != null && count > 0,
                onClick = {
                    scope.launch {
                        val n = vm.importShare(p, list!!, choices.toList(), senderChoice)
                        Toast.makeText(context, if (n == 1) "Added 1 expense" else "Added $n expenses", Toast.LENGTH_SHORT).show()
                        close()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(if (count == 1) "Add 1 expense" else "Add $count expenses") }
        },
    ) { padding ->
        if (list == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (sender == null) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Who is ${p.fromName}?", style = MaterialTheme.typography.titleMedium)
                    Text("This is the first time they share with you. Pick them if you already added them, so their " +
                        "balance stays together.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(senderChoice == null, { senderChoice = null }, label = { Text("New person") })
                        people.forEach { person ->
                            FilterChip(senderChoice == person.id, { senderChoice = person.id }, label = { Text(person.nickname) })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item {
                val byKind = list.groupingBy { it.kind }.eachCount()
                Text(
                    listOfNotNull(
                        byKind[IncomingKind.NEW]?.let { "$it new" },
                        byKind[IncomingKind.UPDATE]?.let { "$it updated" },
                        byKind[IncomingKind.ALREADY_ADDED]?.let { "$it already added" },
                        byKind[IncomingKind.POSSIBLE_DUPLICATE]?.let { "$it possible duplicate" + if (it > 1) "s" else "" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            itemsIndexed(list) { i, inc ->
                IncomingRow(inc, p.fromName, choices.getOrNull(i) ?: ImportChoice.SKIP) { choices[i] = it }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomingRow(inc: Incoming, fromName: String, choice: ImportChoice, onChoice: (ImportChoice) -> Unit) {
    val item = inc.item
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = choice != ImportChoice.SKIP,
            onCheckedChange = { on ->
                onChoice(when {
                    !on -> ImportChoice.SKIP
                    inc.kind == IncomingKind.POSSIBLE_DUPLICATE -> ImportChoice.LINK
                    else -> ImportChoice.ADD
                })
            },
            enabled = inc.kind != IncomingKind.ALREADY_ADDED,
        )
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(itemTitle(item), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Money.format(item.recipientCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(
                Dates.short(item.timestamp.toLocalDate()) + " · total " + Money.format(item.totalCents) + " · " +
                    payerText(item, fromName),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (inc.kind) {
                    IncomingKind.NEW -> "New"
                    IncomingKind.UPDATE -> "Changed since you added it: replaces your copy"
                    IncomingKind.ALREADY_ADDED -> "Already added"
                    IncomingKind.POSSIBLE_DUPLICATE -> {
                        val e = inc.existing!!
                        "Looks like your " + (e.merchant ?: e.note.ifBlank { "expense" }) + " of " +
                            Dates.short(e.timestamp.toLocalDate())
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (inc.kind == IncomingKind.POSSIBLE_DUPLICATE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
            if (inc.kind == IncomingKind.POSSIBLE_DUPLICATE && choice != ImportChoice.SKIP) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(choice == ImportChoice.LINK, { onChoice(ImportChoice.LINK) }, label = { Text("It's the same: split mine") })
                    FilterChip(choice == ImportChoice.ADD, { onChoice(ImportChoice.ADD) }, label = { Text("Add as new") })
                }
            }
        }
    }
}

private fun itemTitle(item: ShareItem): String =
    item.seller?.trim()?.takeIf { it.isNotEmpty() } ?: item.note.trim().ifEmpty { "Expense" }

private fun payerText(item: ShareItem, fromName: String): String = when {
    item.paidBy == item.to -> "you paid"
    item.people[item.paidBy].name == fromName -> "$fromName paid"
    else -> item.people[item.paidBy].name.ifBlank { "someone else" } + " paid"
}
