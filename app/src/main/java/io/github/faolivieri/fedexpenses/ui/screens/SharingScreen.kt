package io.github.faolivieri.fedexpenses.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlin.math.abs

/** People you split expenses with, and what each of you owes the other. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScreen(vm: MainViewModel, onOpenPerson: (Long) -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sharing") }) },
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
        PersonDialog(null, onDismiss = { adding = false }, onSave = { name ->
            adding = false
            scope.launch { vm.savePerson(Person(nickname = name)) }
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(person.nickname, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit person") } },
            )
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
                }
            }) { e ->
                EntryRow(e, person, onClick = {
                    when (e) {
                        is SharedEntry.Expense -> onOpenTransaction(e.tx.id)
                        is SharedEntry.Payment -> settling = e.settlement
                    }
                })
            }
        }
    }

    if (editing) {
        PersonDialog(
            person,
            onDismiss = { editing = false },
            onSave = { name -> editing = false; scope.launch { vm.savePerson(person.copy(nickname = name)) } },
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

@Composable
private fun EntryRow(e: SharedEntry, person: Person, onClick: () -> Unit) {
    val nickname = person.nickname
    val money = LocalMoneyColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (e is SharedEntry.Payment) Icons.Filled.Payments else Icons.Filled.Group, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val (title, detail) = when (e) {
                is SharedEntry.Expense -> {
                    val name = e.tx.merchant?.trim().orEmpty().ifEmpty { e.tx.note.trim() }.ifEmpty { "Expense" }
                    val payer = when (e.tx.paidBy) {
                        null -> "you paid"
                        person.id -> "$nickname paid"
                        else -> "someone else paid"
                    }
                    name to "Total ${Money.format(e.tx.amountCents)} · $payer · their part ${Money.format(e.theirCents)}"
                }
                is SharedEntry.Payment -> {
                    val s = e.settlement
                    (if (s.fromMe) "You paid $nickname" else "$nickname paid you") to s.note.ifBlank { "Settle-up" }
                }
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Dates.short(e.timestamp.toLocalDate()) + " · " + detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** Adds a person, or renames / deletes [initial]. */
@Composable
fun PersonDialog(initial: Person?, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial?.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add person" else "Edit person") },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("Nickname") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettleDialog(
    initial: Settlement?, person: Person, balance: Long, accounts: List<Account>,
    onDismiss: () -> Unit, onSave: (Settlement) -> Unit, onDelete: (Long) -> Unit,
) {
    var amount by remember { mutableStateOf(Money.editable(initial?.amountCents ?: abs(balance))) }
    var fromMe by remember { mutableStateOf(initial?.fromMe ?: (balance < 0)) }
    var accountId by remember { mutableLongStateOf(initial?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
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
                Text("Account", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a -> FilterChip(a.id == accountId, { accountId = a.id }, label = { Text(a.name) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0 && accountId != 0L, onClick = {
                onSave((initial ?: Settlement(personId = person.id, timestamp = System.currentTimeMillis(),
                    amountCents = 0, fromMe = fromMe, accountId = accountId))
                    .copy(amountCents = cents!!, fromMe = fromMe, accountId = accountId))
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
}
