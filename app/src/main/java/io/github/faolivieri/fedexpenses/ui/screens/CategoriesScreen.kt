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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.Category
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.CategoryBadge
import io.github.faolivieri.fedexpenses.ui.components.CategoryEditorDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: MainViewModel, onOpenSellerRule: (Long) -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val usage by vm.categoryUsage.collectAsStateWithLifecycle()
    // 0 = expense categories, 1 = income categories, 2 = seller rules
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = if (tab == 1) TxType.INCOME else TxType.EXPENSE
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (tab == 2) onOpenSellerRule(0) else creating = true }) {
                Icon(Icons.Filled.Add, if (tab == 2) "New seller rule" else "New category")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Expenses") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Income") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Sellers") })
            }
            if (tab == 2) {
                SellerRulesList(vm, onOpen = onOpenSellerRule)
                return@Column
            }
            val shown = categories.filter { it.type == type || it.isSystem }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(c)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            val n = usage[c.id] ?: 0
                            Text(
                                (if (c.isSystem) "Default for new sellers · " else "") + "$n transaction" + (if (n == 1) "" else "s"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val initial = editing
        CategoryEditorDialog(
            initial = initial, defaultType = type, suggestedColorIndex = categories.size,
            onDismiss = { creating = false; editing = null },
            onSave = { c -> scope.launch { vm.saveCategory(c) }; creating = false; editing = null },
            onDelete = initial?.let { { deleting = it; editing = null } },
        )
    }

    deleting?.let { c ->
        val n = usage[c.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${c.name}?") },
            text = { if (n > 0) Text("Its $n transaction${if (n == 1) "" else "s"} will become Uncategorized.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(c); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
