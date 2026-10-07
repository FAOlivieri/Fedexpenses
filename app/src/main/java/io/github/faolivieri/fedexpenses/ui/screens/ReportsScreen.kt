package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.Granularity
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.ReportSeries
import io.github.faolivieri.fedexpenses.ui.SeriesKey
import io.github.faolivieri.fedexpenses.ui.components.EmptyState
import io.github.faolivieri.fedexpenses.ui.components.IconBadge
import io.github.faolivieri.fedexpenses.ui.components.LegendItem
import io.github.faolivieri.fedexpenses.ui.components.LineChart
import io.github.faolivieri.fedexpenses.ui.components.LineSeries
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(vm: MainViewModel) {
    val cfg by vm.report.collectAsStateWithLifecycle()
    val data by vm.reportData.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val earliest by vm.earliestMonth.collectAsStateWithLifecycle()
    val money = LocalMoneyColors.current
    var showTable by rememberSaveable { mutableStateOf(false) }

    fun colorOf(s: ReportSeries): Color = when (s.key) {
        SeriesKey.AllExpenses -> money.expense
        SeriesKey.AllIncomes -> money.income
        is SeriesKey.Cat -> Color(s.color ?: 0xFF888888.toInt())
    }

    fun toggle(key: SeriesKey) {
        vm.report.value = cfg.copy(series = if (key in cfg.series) cfg.series - key else cfg.series + key)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // Range and granularity, all in one block above the chart.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.width(180.dp)) {
                    Granularity.entries.forEachIndexed { i, g ->
                        SegmentedButton(cfg.granularity == g, { vm.report.value = cfg.copy(granularity = g) },
                            SegmentedButtonDefaults.itemShape(i, 2), icon = {}) {
                            Text(if (g == Granularity.MONTH) "Monthly" else "Yearly", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val now = YearMonth.now()
                AssistChip({ vm.report.value = cfg.copy(from = now.minusMonths(11), to = now) }, { Text("Last 12 months") })
                AssistChip({ vm.report.value = cfg.copy(from = YearMonth.of(now.year, 1), to = now) }, { Text("This year") })
                AssistChip({ vm.report.value = cfg.copy(from = YearMonth.of(now.year - 2, 1), to = now) }, { Text("3 years") })
                AssistChip({ vm.report.value = cfg.copy(from = earliest ?: now.minusYears(1), to = now) }, { Text("All time") })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MonthStepper("From", cfg.from, Modifier.weight(1f)) { m ->
                    vm.report.value = cfg.copy(from = m, to = if (m > cfg.to) m else cfg.to)
                }
                MonthStepper("To", cfg.to, Modifier.weight(1f)) { m ->
                    vm.report.value = cfg.copy(to = m, from = if (m < cfg.from) m else cfg.from)
                }
            }

            val d = data
            val visible = d?.series.orEmpty()
            if (d == null) {
                Spacer(Modifier.height(260.dp))
            } else if (visible.isEmpty()) {
                EmptyState("Pick at least one series below")
            } else {
                LineChart(
                    labels = d.labels,
                    series = visible.map { LineSeries(it.name, colorOf(it), it.values) },
                    modifier = Modifier.fillMaxWidth().height(260.dp).padding(start = 8.dp, end = 8.dp, top = 12.dp),
                )
                Text("Tap or drag on the chart to read values", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                // Legend doubles as the summary: total and average per bucket for every series.
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val per = if (cfg.granularity == Granularity.MONTH) "mo" else "yr"
                    visible.forEach { s ->
                        LegendItem(s.name, colorOf(s), trailing = "${Money.format(s.total)} · avg ${Money.format(s.average)}/$per")
                    }
                }
                TextButton(onClick = { showTable = !showTable }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showTable) "Hide values table" else "Show values table")
                }
                if (showTable) ValuesTable(d.labels, visible)
            }

            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { SectionTitle("Series") }
                TextButton(onClick = { vm.report.value = cfg.copy(series = emptyList()) }, enabled = cfg.series.isNotEmpty()) {
                    Text("Clear")
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(SeriesKey.AllExpenses in cfg.series, { toggle(SeriesKey.AllExpenses) },
                    label = { Text("All expenses") })
                FilterChip(SeriesKey.AllIncomes in cfg.series, { toggle(SeriesKey.AllIncomes) },
                    label = { Text("All incomes") })
            }
            listOf(TxType.EXPENSE to "Expense categories", TxType.INCOME to "Income categories").forEach { (type, title) ->
                val cats = categories.filter { it.type == type || (type == TxType.EXPENSE && it.isSystem) }
                if (cats.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cats.forEach { c ->
                            val key = SeriesKey.Cat(c.id)
                            FilterChip(key in cfg.series, { toggle(key) }, label = { Text(c.name) },
                                leadingIcon = { IconBadge(c.icon, c.color, 20.dp) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStepper(label: String, value: YearMonth, modifier: Modifier, onChange: (YearMonth) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Dates.monthShort(value), style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = { onChange(value.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") }
    }
}

/** Accessible fallback: every value the chart plots, as text. */
@Composable
private fun ValuesTable(labels: List<String>, series: List<ReportSeries>) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Column {
            Text("", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
            labels.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp)) }
            Text("Total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp))
        }
        series.forEach { s ->
            Column(Modifier.padding(start = 20.dp), horizontalAlignment = Alignment.End) {
                Text(s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(vertical = 6.dp))
                s.values.forEach { v ->
                    Text(Money.plain(v).let { if (v < 0) "−$it" else it }, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End, modifier = Modifier.padding(vertical = 6.dp))
                }
                Text(Money.plain(s.total), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
    HorizontalDivider(Modifier.padding(16.dp))
}
