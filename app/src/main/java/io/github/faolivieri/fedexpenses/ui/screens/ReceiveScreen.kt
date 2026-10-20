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
import io.github.faolivieri.fedexpenses.data.IncomingKind
import io.github.faolivieri.fedexpenses.data.ShareItem
import io.github.faolivieri.fedexpenses.data.SharePreview
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.toLocalDate
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Reviews what another phone shared (expenses, settle-ups, a starting balance), then adds the chosen entries. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiveScreen(vm: MainViewModel, onDone: () -> Unit) {
    val payload by vm.incomingShare.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val p = payload ?: run { LaunchedEffect(Unit) { onDone() }; return }
    var preview by remember(p) { mutableStateOf<SharePreview?>(null) }
    /** The person chosen as the sender; null adds them as a new person. */
    var senderChoice by remember(p) { mutableStateOf<Long?>(null) }
    val choices = remember(p) { mutableStateListOf<ImportChoice>() }
    val paymentChoices = remember(p) { mutableStateListOf<ImportChoice>() }
    var takeStarting by remember(p) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun default(kind: IncomingKind) = when (kind) {
        IncomingKind.ALREADY_ADDED -> ImportChoice.SKIP
        IncomingKind.POSSIBLE_DUPLICATE -> ImportChoice.LINK
        else -> ImportChoice.ADD
    }

    LaunchedEffect(p) {
        val pv = vm.previewShare(p)
        senderChoice = pv.sender?.id
        choices.clear()
        choices.addAll(pv.items.map { default(it.kind) })
        paymentChoices.clear()
        paymentChoices.addAll(pv.payments.map { default(it.kind) })
        takeStarting = p.startingCents != null && -p.startingCents != (pv.sender?.startingBalanceCents ?: 0)
        preview = pv
    }

    fun close() {
        vm.incomingShare.value = null
        onDone()
    }
    BackHandler(onBack = ::close)

    val pv = preview
    val count = choices.count { it != ImportChoice.SKIP } + paymentChoices.count { it != ImportChoice.SKIP } +
        (if (takeStarting) 1 else 0)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = ::close) { Icon(Icons.Filled.Close, "Close") } },
                title = { Text("From ${p.fromName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
        bottomBar = {
            Button(
                enabled = pv != null && count > 0,
                onClick = {
                    scope.launch {
                        val n = vm.importShare(p, pv!!, choices.toList(), paymentChoices.toList(), takeStarting, senderChoice)
                        Toast.makeText(context, if (n == 1) "Added 1 entry" else "Added $n entries", Toast.LENGTH_SHORT).show()
                        close()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(if (count == 1) "Add 1 entry" else "Add $count entries") }
        },
    ) { padding ->
        if (pv == null) return@Scaffold
        val from = p.fromName
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (pv.sender == null) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Who is $from?", style = MaterialTheme.typography.titleMedium)
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
                val kinds = pv.items.map { it.kind } + pv.payments.map { it.kind }
                val byKind = kinds.groupingBy { it }.eachCount()
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
            p.startingCents?.let { sent ->
                item {
                    val mine = -sent
                    val current = people.firstOrNull { it.id == senderChoice }?.startingBalanceCents ?: 0
                    EntryReviewRow(
                        kind = if (mine == current) IncomingKind.ALREADY_ADDED else IncomingKind.NEW,
                        title = "Starting balance",
                        amount = Money.format(abs(mine)),
                        detail = if (mine > 0) "$from owes you" else "You owe $from",
                        status = when {
                            mine == current -> "Already set"
                            current == 0L -> "New"
                            else -> "Replaces your starting balance of ${Money.formatSigned(current)}"
                        },
                        choice = if (takeStarting) ImportChoice.ADD else ImportChoice.SKIP,
                        onChoice = { takeStarting = it != ImportChoice.SKIP },
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            itemsIndexed(pv.items) { i, inc ->
                val item = inc.item
                EntryReviewRow(
                    kind = inc.kind, title = itemTitle(item), amount = Money.format(item.recipientCents),
                    detail = Dates.short(item.timestamp.toLocalDate()) + " · total " + Money.format(item.totalCents) +
                        " · " + payerText(item, from),
                    status = status(inc.kind, "Changed since you added it: replaces your copy") {
                        val e = inc.existing!!
                        "Looks like your " + (e.merchant ?: e.note.ifBlank { "expense" }) + " of " +
                            Dates.short(e.timestamp.toLocalDate())
                    },
                    choice = choices.getOrNull(i) ?: ImportChoice.SKIP, onChoice = { choices[i] = it },
                )
                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            itemsIndexed(pv.payments) { i, inc ->
                val pay = inc.payment
                EntryReviewRow(
                    kind = inc.kind, title = if (pay.fromSender) "$from paid you" else "You paid $from",
                    amount = Money.format(pay.amountCents),
                    detail = Dates.short(pay.timestamp.toLocalDate()) + " · " + pay.note.ifBlank { "Settle-up" },
                    status = status(inc.kind, "Changed since you added it: replaces your copy") {
                        "Looks like your settle-up of " + Dates.short(inc.existing!!.timestamp.toLocalDate())
                    },
                    choice = paymentChoices.getOrNull(i) ?: ImportChoice.SKIP, onChoice = { paymentChoices[i] = it },
                )
                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun status(kind: IncomingKind, update: String, duplicate: () -> String): String = when (kind) {
    IncomingKind.NEW -> "New"
    IncomingKind.UPDATE -> update
    IncomingKind.ALREADY_ADDED -> "Already added"
    IncomingKind.POSSIBLE_DUPLICATE -> duplicate()
}

/** One received entry with a checkbox; a possible duplicate also offers to attach it to yours or add it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryReviewRow(
    kind: IncomingKind, title: String, amount: String, detail: String, status: String,
    choice: ImportChoice, onChoice: (ImportChoice) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = choice != ImportChoice.SKIP,
            onCheckedChange = { on ->
                onChoice(when {
                    !on -> ImportChoice.SKIP
                    kind == IncomingKind.POSSIBLE_DUPLICATE -> ImportChoice.LINK
                    else -> ImportChoice.ADD
                })
            },
            enabled = kind != IncomingKind.ALREADY_ADDED,
        )
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = if (kind == IncomingKind.POSSIBLE_DUPLICATE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            if (kind == IncomingKind.POSSIBLE_DUPLICATE && choice != ImportChoice.SKIP) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(choice == ImportChoice.LINK, { onChoice(ImportChoice.LINK) }, label = { Text("It's the same as mine") })
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
