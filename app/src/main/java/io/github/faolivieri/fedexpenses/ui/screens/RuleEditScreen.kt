package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.Defaults
import io.github.faolivieri.fedexpenses.data.NotificationRule
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.notify.ParseResult
import io.github.faolivieri.fedexpenses.notify.RuleEngine
import io.github.faolivieri.fedexpenses.ui.AppEntry
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.SettingsViewModel
import io.github.faolivieri.fedexpenses.ui.components.TypeToggle
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Money
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(vm: SettingsViewModel, main: MainViewModel, ruleId: Long, onDone: () -> Unit) {
    val accounts by main.accounts.collectAsStateWithLifecycle()
    val captured by vm.captured.collectAsStateWithLifecycle()

    var loaded by rememberSaveable { mutableStateOf(ruleId == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf(Defaults.GOOGLE_WALLET_PATTERN) }
    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var accountId by rememberSaveable { mutableLongStateOf(0L) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var sampleTitle by rememberSaveable { mutableStateOf("SPAR") }
    var sampleText by rememberSaveable { mutableStateOf("€12.50 with Visa •• 1234") }
    var pickApp by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        if (ruleId != 0L && !loaded) {
            vm.getRule(ruleId)?.let {
                name = it.name; pkg = it.packageName; pattern = it.pattern
                type = it.type; accountId = it.accountId; enabled = it.enabled
            }
            loaded = true
        }
    }
    LaunchedEffect(accounts) { if (accountId == 0L && accounts.isNotEmpty()) accountId = accounts.first().id }

    val patternError = (RuleEngine.parse(pattern, "", "") as? ParseResult.InvalidPattern)?.message
    val canSave = name.isNotBlank() && pkg.isNotBlank() && pattern.isNotBlank() && patternError == null && accountId != 0L

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (ruleId == 0L) "Watch an app" else "Edit rule") },
                actions = {
                    if (ruleId != 0L) IconButton(onClick = { vm.deleteRule(ruleId); onDone() }) {
                        Icon(Icons.Filled.Delete, "Delete rule")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                enabled = canSave && loaded,
                onClick = {
                    vm.saveRule(NotificationRule(ruleId, name.trim(), pkg.trim(), pattern, type, accountId, enabled))
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Save") }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pkg, { pkg = it.trim() }, label = { Text("App package") }, singleLine = true,
                supportingText = { vm.appLabel(pkg)?.let { Text(it) } },
                trailingIcon = { IconButton(onClick = { pickApp = true }) { Icon(Icons.Filled.Apps, "Pick app") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Creates", style = MaterialTheme.typography.labelLarge)
            TypeToggle(type, { type = it }, modifier = Modifier.fillMaxWidth())
            Text("Account", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> FilterChip(a.id == accountId, { accountId = a.id }, label = { Text(a.name) }) }
            }

            OutlinedTextField(
                pattern, { pattern = it }, label = { Text("Patterns (regular expressions)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                isError = patternError != null,
                supportingText = {
                    Text(patternError ?: "One pattern per line; the first that matches wins. Each is matched " +
                        "against the notification's title, a line break, then its text. Capture the amount as " +
                        "(?<amount>…) and optionally the seller as (?<merchant>…); without it the title is used.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { pattern = Defaults.GOOGLE_WALLET_PATTERN }) { Text("Reset to Google Wallet pattern") }

            SectionHeader("Try it")
            OutlinedTextField(sampleTitle, { sampleTitle = it }, label = { Text("Notification title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sampleText, { sampleText = it }, label = { Text("Notification text") }, modifier = Modifier.fillMaxWidth())
            ResultLine(RuleEngine.parse(pattern, sampleTitle, sampleText), type)

            val recent = captured.filter { pkg.isBlank() || it.packageName == pkg }
            SectionHeader("Recent notifications from this app")
            if (recent.isEmpty()) {
                Text(
                    "Notifications from watched apps appear here once notification access is granted. " +
                        "Save the rule, make a payment, then come back to fine-tune the pattern.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            recent.take(15).forEach { n ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .clickable { sampleTitle = n.title; sampleText = n.text }
                        .padding(12.dp),
                ) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(n.postedAt)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(n.text, style = MaterialTheme.typography.bodySmall)
                    ResultLine(RuleEngine.parse(pattern, n.title, n.text), type)
                }
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }

    if (pickApp) {
        AppPickerDialog(vm, onDismiss = { pickApp = false }) { app ->
            pkg = app.packageName
            if (name.isBlank()) name = app.label
            pickApp = false
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ResultLine(r: ParseResult, type: TxType) {
    val money = LocalMoneyColors.current
    val (text, color) = when (r) {
        is ParseResult.Match -> "✓ ${if (type == TxType.EXPENSE) "−" else "+"}${Money.format(r.amountCents)} · seller \"${r.merchant}\"" to
            (if (type == TxType.EXPENSE) money.expense else money.income)
        ParseResult.NoMatch -> "✗ No match — no transaction would be created" to MaterialTheme.colorScheme.onSurfaceVariant
        ParseResult.NoAmount -> "✗ Matched, but no amount found in the (?<amount>…) group" to MaterialTheme.colorScheme.error
        is ParseResult.InvalidPattern -> "✗ ${r.message}" to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
}

@Composable
private fun AppPickerDialog(vm: SettingsViewModel, onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    val apps by produceState<List<AppEntry>?>(null) { value = vm.launchableApps() }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick the payment app") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                val shown = apps.orEmpty().filter {
                    query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 10.dp)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

