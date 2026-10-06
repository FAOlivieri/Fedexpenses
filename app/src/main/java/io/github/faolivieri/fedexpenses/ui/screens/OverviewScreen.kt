package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.CategoryTotal
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.CategoryBadge
import io.github.faolivieri.fedexpenses.ui.components.DonutChart
import io.github.faolivieri.fedexpenses.ui.components.DonutSlice
import io.github.faolivieri.fedexpenses.ui.components.EmptyState
import io.github.faolivieri.fedexpenses.ui.components.FitText
import io.github.faolivieri.fedexpenses.ui.components.PeriodSelector
import io.github.faolivieri.fedexpenses.ui.components.moneyColor
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    vm: MainViewModel,
    onAdd: (TxType) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val period by vm.period.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val money = LocalMoneyColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { onAdd(TxType.INCOME) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { FitText("Income", MaterialTheme.typography.labelLarge) },
                    containerColor = money.income, contentColor = onColor(money.income),
                )
                ExtendedFloatingActionButton(
                    onClick = { onAdd(TxType.EXPENSE) },
                    icon = { Icon(Icons.Filled.Remove, null) }, text = { FitText("Expense", MaterialTheme.typography.labelLarge) },
                    containerColor = money.expense, contentColor = onColor(money.expense),
                )
            }
        },
    ) { padding ->
        val o = overview
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { PeriodSelector(period, { vm.period.value = it }) }
            if (o == null) return@LazyColumn

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TotalCard("Income", o.incomeCents, money.income, Modifier.weight(1f))
                    TotalCard("Expenses", o.expenseCents, money.expense, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Balance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(Money.formatSigned(o.balanceCents), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = if (o.balanceCents < 0) money.expense else money.income)
                }
            }

            item { SectionTitle("Expenses by category") }
            if (o.expensesByCategory.isEmpty()) {
                item { EmptyState("No expenses in this period") }
            } else {
                item {
                    DonutChart(
                        o.expensesByCategory.map { DonutSlice(it.cents, Color(it.category.color)) },
                        Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(o.expenseCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                items(o.expensesByCategory, key = { "e" + it.category.id }) {
                    CategoryRow(it, o.expenseCents, TxType.EXPENSE) { onCategoryClick(it.category.id) }
                }
            }

            if (o.incomeByCategory.isNotEmpty()) {
                item { SectionTitle("Income by category") }
                items(o.incomeByCategory, key = { "i" + it.category.id }) {
                    CategoryRow(it, o.incomeCents, TxType.INCOME) { onCategoryClick(it.category.id) }
                }
            }
        }
    }
}

/** Readable text on a filled button whose color changes between light and dark themes. */
private fun onColor(bg: Color): Color = if (bg.luminance() > 0.4f) Color.Black else Color.White

@Composable
private fun TotalCard(label: String, cents: Long, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            FitText(label, MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FitText(Money.format(cents), MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun CategoryRow(t: CategoryTotal, total: Long, type: TxType, onClick: () -> Unit) {
    val share = if (total > 0) t.cents.toFloat() / total else 0f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(t.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                Text(Money.format(t.cents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = moneyColor(type))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0.01f, 1f)).height(6.dp)
                        .background(Color(t.category.color), RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.width(8.dp))
                Text("%.0f%% · %d".format(share * 100, t.count), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
            }
        }
    }
}

