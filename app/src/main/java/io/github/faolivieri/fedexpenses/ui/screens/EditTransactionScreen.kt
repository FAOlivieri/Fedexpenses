package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Share
import io.github.faolivieri.fedexpenses.ui.rememberNicknameGate
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import io.github.faolivieri.fedexpenses.data.Category
import io.github.faolivieri.fedexpenses.data.Frequency
import io.github.faolivieri.fedexpenses.data.Person
import io.github.faolivieri.fedexpenses.data.Recurrence
import io.github.faolivieri.fedexpenses.data.Recurrences
import io.github.faolivieri.fedexpenses.data.SplitPart
import io.github.faolivieri.fedexpenses.data.Splits
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxSource
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.AccountEditorDialog
import io.github.faolivieri.fedexpenses.ui.components.CategoryEditorDialog
import io.github.faolivieri.fedexpenses.ui.components.CategoryGrid
import io.github.faolivieri.fedexpenses.ui.components.IconBadge
import io.github.faolivieri.fedexpenses.ui.components.TypeToggle
import io.github.faolivieri.fedexpenses.ui.components.moneyColor
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.toLocalDate
import io.github.faolivieri.fedexpenses.util.toMillis
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionScreen(
    vm: MainViewModel,
    txId: Long,
    initialType: TxType,
    onDone: () -> Unit,
    /** Prefill for a new entry, e.g. from a payment notification whose details Android hid. */
    initialTimestamp: Long = 0,
    initialAccountId: Long = 0,
    /** Opens the seller rule that categorizes this seller (or a new one for it). */
    onSellerRule: (String) -> Unit = {},
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val usage by vm.categoryUsage.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<Transaction?>(null) }
    var loaded by rememberSaveable { mutableStateOf(txId == 0L) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable {
        mutableStateOf((if (initialTimestamp > 0) initialTimestamp.toLocalDate() else LocalDate.now()).toString())
    }
    var categoryId by rememberSaveable { mutableLongStateOf(0L) }
    var accountId by rememberSaveable { mutableLongStateOf(initialAccountId) }
    var note by rememberSaveable { mutableStateOf("") }
    var seller by rememberSaveable { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var newAccount by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf<Recurrence?>(null) }
    var frequency by rememberSaveable { mutableStateOf<Frequency?>(null) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showEndDate by remember { mutableStateOf(false) }
    /** Edited payment of a series, waiting for "only this one" or "this and following". */
    var seriesChoice by remember { mutableStateOf<Transaction?>(null) }
    /** Participants (person id, 0 for you) and their parts in basis points; empty when not split. */
    var split by rememberSaveable { mutableStateOf<List<Pair<Long, Int>>>(emptyList()) }
    /** Who paid: a person id, or 0 for you. */
    var payer by rememberSaveable { mutableLongStateOf(0L) }
    var newPerson by remember { mutableStateOf(false) }
    val withNickname = rememberNicknameGate(vm)
    val context = LocalContext.current

    LaunchedEffect(txId) {
        if (txId != 0L) {
            val tx = vm.loadTransaction(txId)
            original = tx
            val r = tx?.recurrenceId?.let { vm.getRecurrence(it) }
            recurrence = r
            if (tx != null && !loaded) {
                type = tx.type
                amount = Money.editable(tx.amountCents)
                date = tx.timestamp.toLocalDate().toString()
                categoryId = tx.categoryId
                accountId = tx.accountId
                note = tx.note
                seller = tx.merchant.orEmpty()
                frequency = r?.frequency
                endDay = r?.endDay
                split = vm.loadSplits(txId).map { (it.personId ?: 0L) to it.basisPoints }.sortedBy { it.first != 0L }
                payer = tx.paidBy ?: 0L
                loaded = true
            }
        }
    }
    LaunchedEffect(accounts) {
        if (accountId == 0L && accounts.isNotEmpty() && txId == 0L) accountId = accounts.first().id
    }

    val cents = Money.parseCents(amount)
    val splitShown = type == TxType.EXPENSE && frequency == null
    val splitParts = split.map { (id, bp) -> SplitPart(transactionId = 0, personId = id.takeIf { it != 0L }, basisPoints = bp) }
    val splitValid = !splitShown || split.isEmpty() || split.sumOf { it.second } == Splits.WHOLE
    val canSave = cents != null && cents > 0 && categoryId != 0L && accountId != 0L && splitValid

    /** Saves; with [shareWith], then shares the saved expense with that person. */
    fun save(shareWith: Long? = null) {
        triedSave = true
        if (!canSave || (txId != 0L && original == null)) return
        val day = LocalDate.parse(date)
        val base = original
        // Keep the original time of day; new entries get "now" so same-day order is natural.
        val time = (base?.timestamp ?: initialTimestamp.takeIf { it > 0 })?.let { it - it.toLocalDate().toMillis() }
            ?: (LocalTime.now().toSecondOfDay() * 1000L)
        // null keeps the existing split.
        val splits: List<SplitPart>? = when {
            frequency != null -> null
            !splitShown -> emptyList()
            else -> splitParts
        }
        val tx = (base ?: Transaction(timestamp = 0, amountCents = 0, type = type, categoryId = 0, accountId = 0))
            .copy(timestamp = day.toMillis() + time, amountCents = cents!!, type = type,
                categoryId = categoryId, accountId = accountId, note = note.trim(),
                merchant = seller.trim().ifEmpty { null },
                paidBy = if (splits == null) base?.paidBy else payer.takeIf { it != 0L && splits.isNotEmpty() },
                shareVersion = base?.let { if (it.shareId != null) it.shareVersion + 1 else it.shareVersion } ?: 0)
        val r = recurrence
        val f = frequency
        val originalDay = base?.timestamp?.toLocalDate()
        when {
            r == null && f == null && shareWith != null -> {
                scope.launch {
                    val id = vm.saveTransactionNow(tx, splits)
                    vm.share(context, shareWith, listOf(id))
                    onDone()
                }
                return
            }
            r == null && f == null -> vm.saveTransaction(tx, splits)
            r == null -> {
                vm.startRecurring(tx, f!!, endDay)
                if (base == null && day > LocalDate.now()) {
                    Toast.makeText(context, "The first payment will be added on ${Dates.short(day)}", Toast.LENGTH_LONG).show()
                }
            }
            f == null -> {
                vm.stopRecurring(r.id)
                vm.saveTransaction(tx.copy(recurrenceId = null), splits)
            }
            // A new schedule applies from this payment on.
            f != r.frequency || endDay != r.endDay || day != originalDay -> vm.updateSeries(tx, originalDay!!, f, endDay)
            base != null && seriesFieldsChanged(base, tx) -> { seriesChoice = tx; return }
            else -> vm.saveTransaction(tx)
        }
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Text(when {
                        txId != 0L -> "Edit transaction"
                        type == TxType.EXPENSE -> "New expense"
                        else -> "New income"
                    })
                },
                actions = {
                    if (txId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
        bottomBar = {
            Button(onClick = { save() }, enabled = loaded,
                modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp).height(52.dp)) {
                Text("Save")
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TypeToggle(type, {
                type = it
                if (categories.firstOrNull { c -> c.id == categoryId }?.let { c -> c.type != it && !c.isSystem } == true) categoryId = 0
            }, modifier = Modifier.fillMaxWidth())

            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount") },
                suffix = { Text(Money.symbol) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = moneyColor(type), fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = triedSave && (cents == null || cents <= 0),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (txId == 0L) LaunchedEffect(Unit) { focus.requestFocus() }
            if (splitShown && split.isNotEmpty() && cents != null && cents > 0) {
                val mine = Splits.parts(cents, splitParts, payer.takeIf { it != 0L })[null] ?: 0
                Text("Your share: " + Money.format(mine), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = moneyColor(type))
            }

            Row(
                Modifier.fillMaxWidth().clickable { showDate = true }
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(Dates.dayHeader(LocalDate.parse(date)), style = MaterialTheme.typography.bodyLarge)
            }

            RepeatSection(
                frequency = frequency, onFrequency = { frequency = it },
                endDay = endDay, onPickEnd = { showEndDate = true }, onClearEnd = { endDay = null },
                next = nextRepeat(recurrence, frequency, endDay, LocalDate.parse(date), original, isNew = txId == 0L),
            )

            OutlinedTextField(
                value = seller, onValueChange = { seller = it }, label = { Text("Seller") },
                leadingIcon = { Icon(Icons.Filled.Storefront, null) },
                trailingIcon = if (seller.isNotBlank()) {
                    { TextButton(onClick = { onSellerRule(seller.trim()) }) { Text("Rule") } }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Note") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.titleSmall,
                color = if (triedSave && categoryId == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            val shown = categories
                .filter { (it.type == type && !it.isSystem) || (it.isSystem && it.id == categoryId) }
                .sortedByDescending { usage[it.id] ?: 0 }
            CategoryGrid(shown, categoryId, onSelect = { categoryId = it.id }, onNew = { newCategory = true })

            if (splitShown) {
                SplitSection(
                    people = people, split = split, payer = payer, cents = cents?.takeIf { it > 0 },
                    onSplit = { split = it; if (it.none { (id, _) -> id == payer }) payer = 0L },
                    onPayer = { payer = it },
                    onNewPerson = { newPerson = true },
                )
                if (recurrence == null) {
                    split.filter { it.first != 0L }.forEach { (id, _) ->
                        val name = people.firstOrNull { it.id == id }?.nickname.orEmpty()
                        OutlinedButton(onClick = { withNickname { save(shareWith = id) } }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save and share with $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text("Account", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a ->
                    FilterChip(a.id == accountId, { accountId = a.id }, label = { Text(a.name) },
                        leadingIcon = { IconBadge(a.icon, a.color, 20.dp) })
                }
                FilterChip(false, { newAccount = true }, label = { Text("New account") },
                    leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
            }

            if (original?.source == TxSource.RECURRING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Repeat, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Added automatically by a repeating payment",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (original?.source == TxSource.NOTIFICATION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Detected from a payment notification",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / 86_400_000).toString() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (newCategory) {
        CategoryEditorDialog(
            initial = null, defaultType = type, suggestedColorIndex = categories.size,
            onDismiss = { newCategory = false },
            onSave = { c ->
                newCategory = false
                scope.launch {
                    val id = vm.saveCategory(c)
                    if (c.type == type) categoryId = id
                }
            },
        )
    }

    if (newAccount) {
        AccountEditorDialog(
            initial = null, suggestedColorIndex = accounts.size,
            onDismiss = { newAccount = false },
            onSave = { a -> newAccount = false; scope.launch { accountId = vm.saveAccount(a) } },
        )
    }

    if (newPerson) {
        PersonDialog(null, onDismiss = { newPerson = false }, onSave = { name, starting ->
            newPerson = false
            scope.launch {
                val id = vm.savePerson(Person(nickname = name, startingBalanceCents = starting))
                split = toggled(split, id)
            }
        })
    }

    if (showEndDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (endDay?.let(LocalDate::ofEpochDay) ?: LocalDate.parse(date).plusYears(1))
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDay = it / 86_400_000 }
                    showEndDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDate = false }) { Text("Cancel") } },
        ) { DatePicker(state, title = { Text("Repeat until", Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }

    seriesChoice?.let { pending ->
        AlertDialog(
            onDismissRequest = { seriesChoice = null },
            title = { Text("Change repeating payment") },
            text = { Text("Apply your changes to this payment only, or also to the ones that follow it?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        seriesChoice = null
                        vm.updateSeries(pending, original!!.timestamp.toLocalDate(), frequency!!, endDay)
                        onDone()
                    }) { Text("This and following") }
                    TextButton(onClick = { seriesChoice = null; vm.saveTransaction(pending); onDone() }) {
                        Text("Only this one")
                    }
                    TextButton(onClick = { seriesChoice = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmDelete) {
        val r = recurrence
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = if (r != null) {
                { Text("This payment repeats ${r.frequency.label.lowercase()}.") }
            } else null,
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (r != null) {
                        TextButton(onClick = {
                            vm.stopRecurring(r.id); vm.deleteTransaction(txId); confirmDelete = false; onDone()
                        }) { Text("Delete and stop repeating", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { vm.deleteTransaction(txId); confirmDelete = false; onDone() }) {
                        Text(if (r != null) "Delete only this one" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                    if (r != null) TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            },
            dismissButton = if (r == null) {
                { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
            } else null,
        )
    }
}

private fun seriesFieldsChanged(a: Transaction, b: Transaction) =
    a.amountCents != b.amountCents || a.type != b.type || a.categoryId != b.categoryId ||
        a.accountId != b.accountId || a.note != b.note || a.merchant != b.merchant

/** Date of the next automatic payment as currently set up in the editor (null: none). */
private fun nextRepeat(
    recurrence: Recurrence?, frequency: Frequency?, endDay: Long?, day: LocalDate, original: Transaction?, isNew: Boolean,
): LocalDate? {
    val f = frequency ?: return null
    val next = when {
        recurrence != null && f == recurrence.frequency && day == original?.timestamp?.toLocalDate() ->
            recurrence.occurrence(recurrence.nextIndex)
        isNew && day > LocalDate.now() -> day
        else -> Recurrences.occurrence(f, day, 1)
    }
    return next.takeIf { endDay == null || it <= LocalDate.ofEpochDay(endDay) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(
    frequency: Frequency?, onFrequency: (Frequency?) -> Unit,
    endDay: Long?, onPickEnd: () -> Unit, onClearEnd: () -> Unit,
    next: LocalDate?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Repeat", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(frequency == null, { onFrequency(null) }, label = { Text("Never") })
            Frequency.entries.forEach { f ->
                FilterChip(frequency == f, { onFrequency(f) }, label = { Text(f.label) },
                    leadingIcon = if (frequency == f) {
                        { Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp)) }
                    } else null)
            }
        }
        if (frequency != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                InputChip(
                    selected = endDay != null, onClick = onPickEnd,
                    label = { Text(endDay?.let { "Until " + Dates.short(LocalDate.ofEpochDay(it)) } ?: "No end date") },
                    leadingIcon = { Icon(Icons.Filled.Event, null, Modifier.size(18.dp)) },
                    trailingIcon = if (endDay != null) {
                        { Icon(Icons.Filled.Close, "Remove end date", Modifier.size(18.dp).clickable(onClick = onClearEnd)) }
                    } else null,
                )
            }
            val today = LocalDate.now()
            Text(
                when {
                    next == null -> "No more payments: the end date has passed."
                    next <= today -> "Payments due up to today are added when you save."
                    else -> "Next: ${Dates.dayHeader(next)}, added automatically at the start of that day."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [split] with [personId] added or removed, parts reset to equal; you are always the first participant. */
private fun toggled(split: List<Pair<Long, Int>>, personId: Long): List<Pair<Long, Int>> {
    val others = split.map { it.first }.filter { it != 0L }
    val ids = if (personId in others) others - personId else others + personId
    if (ids.isEmpty()) return emptyList()
    val all = listOf(0L) + ids
    return all.zip(Splits.equal(all.size))
}

private fun formatPercent(bp: Int): String = BigDecimal.valueOf(bp.toLong(), 2).stripTrailingZeros().toPlainString()

private fun parsePercent(s: String): Int? = s.replace(',', '.').toBigDecimalOrNull()
    ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toInt()?.takeIf { it in 0..Splits.WHOLE }

/**
 * People to split an expense with: a part (percent) for each participant, you included, and who paid.
 * With two participants, editing one part sets the other to the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitSection(
    people: List<Person>, split: List<Pair<Long, Int>>, payer: Long, cents: Long?,
    onSplit: (List<Pair<Long, Int>>) -> Unit, onPayer: (Long) -> Unit, onNewPerson: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Split with", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { p ->
                FilterChip(split.any { it.first == p.id }, { onSplit(toggled(split, p.id)) }, label = { Text(p.nickname) })
            }
            FilterChip(false, onNewPerson, label = { Text("New person") },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        }
        if (split.isEmpty()) return@Column

        val parts = cents?.let {
            val list = split.map { (id, bp) -> SplitPart(transactionId = 0, personId = id.takeIf { i -> i != 0L }, basisPoints = bp) }
            Splits.parts(it, list, payer.takeIf { p -> p != 0L })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            Text("Used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
            Text("Paid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
        }
        split.forEach { (id, bp) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (id == 0L) "You" else people.firstOrNull { it.id == id }?.nickname.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (parts != null) {
                        Text(Money.format(parts[id.takeIf { i -> i != 0L }] ?: 0), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PercentField(bp) { v ->
                    onSplit(split.map { (i, b) ->
                        when {
                            i == id -> i to v
                            split.size == 2 -> i to Splits.WHOLE - v
                            else -> i to b
                        }
                    })
                }
                RadioButton(payer == id, { onPayer(id) }, Modifier.width(48.dp))
            }
        }
        val total = split.sumOf { it.second }
        if (total != Splits.WHOLE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Parts add up to ${formatPercent(total)} %, not 100 %", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSplit(split.map { it.first }.zip(Splits.equal(split.size))) }) { Text("Split equally") }
            }
        }
        if (payer != 0L) {
            Text("Someone else paid, so this doesn't change your account balance.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PercentField(bp: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(formatPercent(bp)) }
    LaunchedEffect(bp) { if (parsePercent(text) != bp) text = formatPercent(bp) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { c -> c.isDigit() || c == '.' || c == ',' }
            parsePercent(text)?.let(onChange)
        },
        suffix = { Text("%") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(110.dp),
    )
}
