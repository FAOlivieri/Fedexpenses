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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.Account
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.AccountEditorDialog
import io.github.faolivieri.fedexpenses.ui.components.IconBadge
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Money
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val balances by vm.accountBalances.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Account?>(null) }
    val scope = rememberCoroutineScope()
    val money = LocalMoneyColors.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Accounts") }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New account") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                val total = balances.values.sum()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Net across all accounts", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(Money.formatSigned(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = if (total < 0) money.expense else money.income)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(accounts, key = { it.id }) { a ->
                val bal = balances[a.id] ?: 0
                Row(
                    Modifier.fillMaxWidth().clickable { editing = a }.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(a.icon, a.color)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text("Net of all transactions", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.formatSigned(bal), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        color = if (bal < 0) money.expense else money.income)
                    if (accounts.size > 1) {
                        IconButton(onClick = { deleting = a }) {
                            Icon(Icons.Filled.Delete, "Delete ${a.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else Spacer(Modifier.width(12.dp))
                }
            }
        }
    }

    if (creating || editing != null) {
        AccountEditorDialog(
            initial = editing, suggestedColorIndex = accounts.size,
            onDismiss = { creating = false; editing = null },
            onSave = { a -> scope.launch { vm.saveAccount(a) }; creating = false; editing = null },
        )
    }

    deleting?.let { a ->
        val others = accounts.filter { it.id != a.id }
        var moveTo by remember(a) { mutableLongStateOf(others.first().id) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${a.name}?") },
            text = {
                Column {
                    Text("Move its transactions to:")
                    others.forEach { o ->
                        FilterChip(o.id == moveTo, { moveTo = o.id }, label = { Text(o.name) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteAccount(a, moveTo); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
