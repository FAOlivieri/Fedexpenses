package io.github.faolivieri.fedexpenses.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import io.github.faolivieri.fedexpenses.data.SharePayload
import io.github.faolivieri.fedexpenses.ui.ShareActions
import io.github.faolivieri.fedexpenses.ui.rememberNicknameGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.Account
import io.github.faolivieri.fedexpenses.data.Person
import io.github.faolivieri.fedexpenses.data.Settlement
import io.github.faolivieri.fedexpenses.data.SharedEntry
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.EmptyState
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.toLocalDate
import io.github.faolivieri.fedexpenses.util.toMillis
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** People you split expenses with, and what each of you owes the other. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScreen(vm: MainViewModel, onOpenPerson: (Long) -> Unit, onReceive: () -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun receive(r: Result<SharePayload>) {
        r.onSuccess { vm.incomingShare.value = it; onReceive() }
            .onFailure { Toast.makeText(context, it.message ?: "Not a Fedexpenses share", Toast.LENGTH_LONG).show() }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { receive(withContext(Dispatchers.IO) { ShareActions.read(context, uri) }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sharing") },
                actions = {
                    IconButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FileOpen, "Open a shared file") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.PersonAdd, "Add person") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (people.isEmpty()) item {
                EmptyState("Add the people you split expenses with, then split a transaction with them from its editor.")
            }
            items(people, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPerson(p.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonBadge(p.nickname)
                    Spacer(Modifier.width(16.dp))
                    Text(p.nickname, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BalanceText(ledger[p.id].orEmpty().sumOf { it.owedCents }, Alignment.End)
                }
            }
        }
    }

    if (adding) {
        PersonDialog(null, onDismiss = { adding = false }, onSave = { name, starting ->
            adding = false
            scope.launch { vm.savePerson(Person(nickname = name, startingBalanceCents = starting)) }
        })
    }
}

/** One person: the balance with them, settle-ups, and every expense split with them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(vm: MainViewModel, personId: Long, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.id == personId } ?: return
    val entries = ledger[personId].orEmpty()
    val balance = entries.sumOf { it.owedCents }
    var editing by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Settlement?>(null) }
    var newSettlement by remember { mutableStateOf(false) }
    /** Expenses selected for sharing (long-press to start). */
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val expenseIds = entries.filterIsInstance<SharedEntry.Expense>().map { it.tx.id }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val withNickname = rememberNicknameGate(vm)
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel selection") } },
                    title = { Text("${selected.size} selected") },
                    actions = {
                        IconButton(onClick = { selected = expenseIds.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton(onClick = {
                            val ids = selected.toList()
                            withNickname { vm.share(context, personId, ids); selected = emptySet() }
                        }) { Icon(Icons.Filled.Share, "Share with ${person.nickname}") }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = { Text(person.nickname, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (expenseIds.isNotEmpty()) IconButton(onClick = { withNickname { vm.share(context, personId, expenseIds) } }) {
                            Icon(Icons.Filled.Share, "Share everything with ${person.nickname}")
                        }
                        IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit person") }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BalanceText(balance, Alignment.CenterHorizontally, large = true)
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { newSettlement = true }) { Text("Settle up") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (entries.isEmpty()) item { EmptyState("Nothing shared with ${person.nickname} yet") }
            items(entries, key = { e ->
                when (e) {
                    is SharedEntry.Expense -> "t${e.tx.id}"
                    is SharedEntry.Payment -> "s${e.settlement.id}"
                    is SharedEntry.Starting -> "start"
                }
            }) { e ->
                val txId = (e as? SharedEntry.Expense)?.tx?.id
                fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
                EntryRow(
                    e, person, people, selected = txId != null && txId in selected,
                    onLongClick = txId?.let { { toggle(it) } },
                    onClick = {
                        when (e) {
                            is SharedEntry.Expense -> if (selected.isNotEmpty()) toggle(e.tx.id) else onOpenTransaction(e.tx.id)
                            is SharedEntry.Payment -> settling = e.settlement
                            is SharedEntry.Starting -> editing = true
                        }
                    },
                )
            }
        }
    }

    if (editing) {
        PersonDialog(
            person,
            onDismiss = { editing = false },
            onSave = { name, starting ->
                editing = false
                scope.launch { vm.savePerson(person.copy(nickname = name, startingBalanceCents = starting)) }
            },
            onDelete = {
                scope.launch {
                    if (vm.deletePerson(person.id)) {
                        editing = false
                        onBack()
                    } else {
                        Toast.makeText(context, "${person.nickname} still has shared transactions or settle-ups",
                            Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    if (newSettlement || settling != null) {
        SettleDialog(
            initial = settling, person = person, balance = balance, accounts = accounts,
            onDismiss = { newSettlement = false; settling = null },
            onSave = { vm.saveSettlement(it); newSettlement = false; settling = null },
            onDelete = { vm.deleteSettlement(it); settling = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    e: SharedEntry, person: Person, people: List<Person>, selected: Boolean, onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val nickname = person.nickname
    val money = LocalMoneyColors.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (e) {
                is SharedEntry.Payment -> Icons.Filled.Payments
                is SharedEntry.Starting -> Icons.Filled.Flag
                is SharedEntry.Expense -> Icons.Filled.Group
            },
            null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val (title, detail) = when (e) {
                is SharedEntry.Expense -> {
                    val name = e.tx.merchant?.trim().orEmpty().ifEmpty { e.tx.note.trim() }.ifEmpty { "Expense" }
                    val payer = when (e.tx.paidBy) {
                        null -> "you paid"
                        person.id -> "$nickname paid"
                        else -> (people.firstOrNull { it.id == e.tx.paidBy }?.nickname ?: "someone else") + " paid"
                    }
                    name to "Total ${Money.format(e.tx.amountCents)} · $payer · their part ${Money.format(e.theirCents)}"
                }
                is SharedEntry.Payment -> {
                    val s = e.settlement
                    (if (s.fromMe) "You paid $nickname" else "$nickname paid you") to s.note.ifBlank { "Settle-up" }
                }
                is SharedEntry.Starting -> "Starting balance" to "Carried over · tap to change"
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e is SharedEntry.Starting) detail else Dates.short(e.timestamp.toLocalDate()) + " · " + detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(Money.formatSigned(e.owedCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            color = when {
                e.owedCents > 0 -> money.income
                e.owedCents < 0 -> money.expense
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
    }
}

/** "owes you" / "you owe" / "settled up", with the amount. Positive [cents]: they owe you. */
@Composable
private fun BalanceText(cents: Long, alignment: Alignment.Horizontal, large: Boolean = false) {
    val money = LocalMoneyColors.current
    Column(horizontalAlignment = alignment) {
        Text(
            when {
                cents > 0 -> "owes you"
                cents < 0 -> "you owe"
                else -> "settled up"
            },
            style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cents != 0L) {
            Text(Money.format(abs(cents)),
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, color = if (cents > 0) money.income else money.expense)
        }
    }
}

@Composable
private fun PersonBadge(nickname: String) {
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(nickname.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Adds a person, or renames / deletes [initial]. [onSave] gets the nickname and the starting balance
 * (positive: they owe you).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonDialog(initial: Person?, onDismiss: () -> Unit, onSave: (String, Long) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial?.nickname.orEmpty()) }
    val start = initial?.startingBalanceCents ?: 0L
    // 0 = no starting balance, 1 = they owe you, -1 = you owe them
    var direction by remember { mutableIntStateOf(start.sign) }
    var amount by remember { mutableStateOf(if (start == 0L) "" else Money.editable(abs(start))) }
    val cents = Money.parseCents(amount)
    val valid = name.isNotBlank() && (direction == 0 || (cents != null && cents > 0))
    val who = name.trim().ifEmpty { "They" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add person" else "Edit person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nickname") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Text("Starting balance", style = MaterialTheme.typography.labelLarge)
                Text("What you already owed each other before tracking it here, e.g. from another app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(direction == 0, { direction = 0 }, label = { Text("None") })
                    FilterChip(direction == 1, { direction = 1 }, label = { Text("$who owes you") })
                    FilterChip(direction == -1, { direction = -1 }, label = { Text("You owe $who") })
                }
                if (direction != 0) {
                    OutlinedTextField(
                        amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Amount") }, suffix = { Text(Money.symbol) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(name.trim(), if (direction == 0) 0L else direction * cents!!)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Records money paid between you and [person], or edits / deletes [initial]. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SettleDialog(
    initial: Settlement?, person: Person, balance: Long, accounts: List<Account>,
    onDismiss: () -> Unit, onSave: (Settlement) -> Unit, onDelete: (Long) -> Unit,
) {
    var amount by remember { mutableStateOf(Money.editable(initial?.amountCents ?: abs(balance))) }
    var fromMe by remember { mutableStateOf(initial?.fromMe ?: (balance < 0)) }
    var accountId by remember { mutableLongStateOf(initial?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var date by remember { mutableStateOf(initial?.timestamp?.toLocalDate() ?: LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    val cents = Money.parseCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Settle up" else "Edit settle-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!fromMe, { fromMe = false }, label = { Text("${person.nickname} paid you") })
                    FilterChip(fromMe, { fromMe = true }, label = { Text("You paid ${person.nickname}") })
                }
                OutlinedTextField(
                    amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Amount") }, suffix = { Text(Money.symbol) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { pickingDate = true }
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(Dates.dayHeader(date), style = MaterialTheme.typography.bodyLarge)
                }
                Text("Account", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a -> FilterChip(a.id == accountId, { accountId = a.id }, label = { Text(a.name) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0 && accountId != 0L, onClick = {
                // Keep the time of day (the original one when editing) so same-day order stays natural.
                val base = initial?.timestamp ?: System.currentTimeMillis()
                val timeOfDay = base - base.toLocalDate().toMillis()
                onSave((initial ?: Settlement(personId = person.id, timestamp = 0,
                    amountCents = 0, fromMe = fromMe, accountId = accountId))
                    .copy(amountCents = cents!!, fromMe = fromMe, accountId = accountId,
                        timestamp = date.toMillis() + timeOfDay))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / 86_400_000) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Splits [count] expenses equally between you and the chosen people, and records who paid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitManyDialog(count: Int, people: List<Person>, onDismiss: () -> Unit, onApply: (List<Long>, Long?) -> Unit) {
    var chosen by remember { mutableStateOf(listOf<Long>()) }
    var payer by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Split 1 expense" else "Split $count expenses") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (people.isEmpty()) {
                    Text("Add people in the Sharing tab first.")
                } else {
                    Text("Equally between you and:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.forEach { p ->
                            FilterChip(p.id in chosen, {
                                chosen = if (p.id in chosen) chosen - p.id else chosen + p.id
                                if (payer != null && payer !in chosen) payer = null
                            }, label = { Text(p.nickname) })
                        }
                    }
                    if (chosen.isNotEmpty()) {
                        Text("Paid by", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(payer == null, { payer = null }, label = { Text("You") })
                            people.filter { it.id in chosen }.forEach { p ->
                                FilterChip(payer == p.id, { payer = p.id }, label = { Text(p.nickname) })
                            }
                        }
                    }
                    Text("Choosing nobody removes the split.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = people.isNotEmpty(), onClick = { onApply(chosen, payer) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks which of [people] to share with. */
@Composable
fun ChoosePersonDialog(people: List<Person>, onDismiss: () -> Unit, onChoose: (Person) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share with") },
        text = {
            Column {
                people.forEach { p ->
                    Text(p.nickname, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onChoose(p) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
