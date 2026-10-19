package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.TxDetails
import io.github.faolivieri.fedexpenses.data.TxSource
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.TxFilter
import io.github.faolivieri.fedexpenses.ui.components.CategoryBadge
import io.github.faolivieri.fedexpenses.ui.components.EmptyState
import io.github.faolivieri.fedexpenses.ui.components.IconBadge
import io.github.faolivieri.fedexpenses.ui.components.PeriodSelector
import io.github.faolivieri.fedexpenses.ui.components.SignedAmount
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.toLocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onAdd: (TxType) -> Unit, onRecurring: () -> Unit) {
    val period by vm.period.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val list by vm.filteredTx.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val money = LocalMoneyColors.current

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.filter.value = filter.copy(query = "") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                        }
                    },
                    title = {
                        TextField(
                            value = filter.query, onValueChange = { vm.filter.value = filter.copy(query = it) },
                            placeholder = { Text("Search notes, sellers, categories") }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Transactions") },
                    actions = {
                        IconButton(onClick = onRecurring) { Icon(Icons.Filled.EventRepeat, "Repeating payments") }
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { showFilters = true }) {
                            BadgedBox(badge = { if (filter.isActive) Badge() }) { Icon(Icons.Filled.FilterList, "Filter") }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(TxType.EXPENSE) }) { Icon(Icons.Filled.Add, "Add transaction") }
        },
    ) { padding ->
        val items = list
        val grouped = remember(items) { items?.groupBy { it.tx.timestamp.toLocalDate() }?.toList() }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { PeriodSelector(period, { vm.period.value = it }) }
            if (filter.isActive) item { ActiveFilters(vm, filter) }
            if (items != null) item {
                val exp = items.filter { it.tx.type == TxType.EXPENSE }.sumOf { it.myCents }
                val inc = items.filter { it.tx.type == TxType.INCOME }.sumOf { it.myCents }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("${items.size} transactions", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("+" + Money.format(inc), style = MaterialTheme.typography.labelMedium, color = money.income)
                    Spacer(Modifier.width(12.dp))
                    Text("−" + Money.format(exp), style = MaterialTheme.typography.labelMedium, color = money.expense)
                }
            }
            if (grouped != null && grouped.isEmpty()) item {
                EmptyState(if (filter.isActive) "No transactions match the filters" else "No transactions in this period")
            }
            grouped?.forEach { (day, txs) ->
                stickyHeader(key = day.toString(), contentType = "day") {
                    val net = txs.sumOf { it.mySignedCents }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Dates.dayHeader(day), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(Money.formatSigned(net), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(txs, key = { it.tx.id }, contentType = { "tx" }) { d ->
                    TransactionRow(d, showAccount = accounts.size > 1) { onOpen(d.tx.id) }
                }
                item(key = "div$day", contentType = "divider") { HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant) }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(filter, categories, accounts, onChange = { vm.filter.value = it })
        }
    }
}

@Composable
fun TransactionRow(d: TxDetails, showAccount: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(d.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val seller = d.tx.merchant?.trim().orEmpty()
            val note = d.tx.note.trim()
            if (seller.isNotEmpty()) DetailLine(Icons.Filled.Storefront, seller, MaterialTheme.colorScheme.onSurface)
            if (note.isNotEmpty() && !note.equals(seller, ignoreCase = true)) {
                DetailLine(Icons.AutoMirrored.Filled.Notes, note, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showAccount) DetailLine(Icons.Filled.AccountBalanceWallet, d.account.name, MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.split.isNotEmpty()) {
                DetailLine(Icons.Filled.Group, "Your share " + Money.format(d.myCents), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (d.tx.recurrenceId != null || d.tx.source == TxSource.RECURRING) {
            Icon(Icons.Filled.Repeat, "Repeating payment",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        } else if (d.tx.source == TxSource.NOTIFICATION) {
            Icon(Icons.Filled.NotificationsActive, "Detected automatically",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        SignedAmount(d.tx.amountCents, d.tx.type)
    }
}

/** One secondary line of a transaction row: a small icon and single-line text. */
@Composable
private fun DetailLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(vm: MainViewModel, f: TxFilter) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        f.type?.let { t ->
            InputChip(selected = true, onClick = { vm.filter.value = f.copy(type = null) },
                label = { Text(if (t == TxType.EXPENSE) "Expenses" else "Income") },
                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) })
        }
        categories.filter { it.id in f.categoryIds }.forEach { c ->
            InputChip(selected = true, onClick = { vm.filter.value = f.copy(categoryIds = f.categoryIds - c.id) },
                label = { Text(c.name) }, avatar = { IconBadge(c.icon, c.color, 20.dp) },
                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) })
        }
        accounts.filter { it.id in f.accountIds }.forEach { a ->
            InputChip(selected = true, onClick = { vm.filter.value = f.copy(accountIds = f.accountIds - a.id) },
                label = { Text(a.name) },
                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) })
        }
        TextButton(onClick = { vm.filter.value = TxFilter() }) { Text("Clear") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    f: TxFilter,
    categories: List<io.github.faolivieri.fedexpenses.data.Category>,
    accounts: List<io.github.faolivieri.fedexpenses.data.Account>,
    onChange: (TxFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(TxFilter(query = f.query)) }) { Text("Reset") }
        }
        Text("Type", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(f.type == null, { onChange(f.copy(type = null)) }, label = { Text("All") })
            FilterChip(f.type == TxType.EXPENSE, { onChange(f.copy(type = TxType.EXPENSE)) }, label = { Text("Expenses") })
            FilterChip(f.type == TxType.INCOME, { onChange(f.copy(type = TxType.INCOME)) }, label = { Text("Income") })
        }
        Spacer(Modifier.height(4.dp))
        Text("Categories", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.filter { f.type == null || it.type == f.type || it.isSystem }.forEach { c ->
                val sel = c.id in f.categoryIds
                FilterChip(sel, { onChange(f.copy(categoryIds = if (sel) f.categoryIds - c.id else f.categoryIds + c.id)) },
                    label = { Text(c.name) }, leadingIcon = { IconBadge(c.icon, c.color, 20.dp) })
            }
        }
        if (accounts.size > 1) {
            Spacer(Modifier.height(4.dp))
            Text("Accounts", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a ->
                    val sel = a.id in f.accountIds
                    FilterChip(sel, { onChange(f.copy(accountIds = if (sel) f.accountIds - a.id else f.accountIds + a.id)) },
                        label = { Text(a.name) }, leadingIcon = { IconBadge(a.icon, a.color, 20.dp) })
                }
            }
        }
    }
}
