package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.MatchType
import io.github.faolivieri.fedexpenses.data.SellerMatcher
import io.github.faolivieri.fedexpenses.data.SellerRule
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.CategoryGrid
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerRuleEditScreen(vm: MainViewModel, ruleId: Long, initialSeller: String, onDone: () -> Unit) {
    val rules by vm.sellerRules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val usage by vm.categoryUsage.collectAsStateWithLifecycle()
    val payments by vm.detectedPayments.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<SellerRule?>(null) }
    var loaded by rememberSaveable { mutableStateOf(ruleId == 0L) }
    var matchType by rememberSaveable { mutableStateOf(MatchType.EXACT) }
    var pattern by rememberSaveable { mutableStateOf(initialSeller) }
    var useLastMatched by rememberSaveable { mutableStateOf(true) }
    var categoryId by rememberSaveable { mutableLongStateOf(0L) } // 0 = none yet
    var testSeller by rememberSaveable { mutableStateOf(initialSeller) }
    var note by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var applyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        if (ruleId != 0L) {
            val r = vm.getSellerRule(ruleId)
            original = r
            if (r != null && !loaded) {
                matchType = r.matchType; pattern = r.pattern; useLastMatched = r.useLastMatched
                categoryId = r.categoryId ?: 0L
                note = r.note.orEmpty()
                if (testSeller.isBlank()) testSeller = r.pattern.takeIf { r.matchType != MatchType.REGEX }.orEmpty()
                loaded = true
            }
        }
    }

    val draft = SellerRule(
        id = ruleId, matchType = matchType, pattern = pattern.trim(), useLastMatched = useLastMatched,
        categoryId = categoryId.takeIf { it != 0L }, position = original?.position ?: Int.MAX_VALUE,
        updatedAt = original?.updatedAt ?: 0, note = note.trim().ifEmpty { null },
    )
    val regexError = if (matchType == MatchType.REGEX) SellerMatcher.regexError(pattern.trim()) else null
    val canSave = loaded && pattern.isNotBlank() && regexError == null && (useLastMatched || categoryId != 0L)

    // Other rules in priority order, with this draft in its place (new rules go last).
    val withDraft = remember(rules, draft) { (rules.filter { it.id != ruleId || ruleId == 0L } + draft) }
    val uncategorizedId = categories.firstOrNull { it.isSystem }?.id
    // Past detected payments whose category this rule decides.
    val governed = remember(withDraft, payments) {
        payments.filter { tx -> SellerMatcher.firstMatch(withDraft, tx.merchant!!)?.id == draft.id }
    }

    fun save(after: suspend (Long) -> Unit = {}) {
        if (!canSave) return
        scope.launch {
            val id = vm.saveSellerRule(draft)
            after(id)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (ruleId == 0L) "New seller rule" else "Edit seller rule") },
                actions = {
                    if (ruleId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete rule") }
                },
            )
        },
        bottomBar = {
            Button(onClick = { save() }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp).height(52.dp)) { Text("Save") }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Match", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MatchType.entries.forEachIndexed { i, t ->
                    SegmentedButton(matchType == t, { matchType = t }, SegmentedButtonDefaults.itemShape(i, MatchType.entries.size),
                        icon = {}) {
                        Text(when (t) { MatchType.EXACT -> "Exact"; MatchType.CONTAINS -> "Contains"; MatchType.REGEX -> "Regex" },
                            maxLines = 1, softWrap = false)
                    }
                }
            }
            OutlinedTextField(
                value = pattern, onValueChange = { pattern = it },
                label = { Text(when (matchType) {
                    MatchType.EXACT -> "Seller name"
                    MatchType.CONTAINS -> "Word or phrase"
                    MatchType.REGEX -> "Regular expression"
                }) },
                textStyle = if (matchType == MatchType.REGEX)
                    MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                isError = regexError != null,
                supportingText = {
                    Text(regexError ?: when (matchType) {
                        MatchType.EXACT -> "Only this exact seller name. Upper/lower case and extra spaces are ignored."
                        MatchType.CONTAINS -> "Any seller name containing this whole word or phrase, e.g. \"lidl\" matches " +
                            "\"LIDL Ljubljana 123\" but \"spar\" doesn't match \"Sparkasse\"."
                        MatchType.REGEX -> "Matched anywhere in the seller name, ignoring case. Example: ^(hofer|lidl)\\b"
                    })
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Category", style = MaterialTheme.typography.titleSmall,
                color = if (!useLastMatched && categoryId == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            ModeOption(
                selected = !useLastMatched, title = "Always this category",
                detail = "Every payment this rule matches gets the category below.",
            ) { useLastMatched = false }
            ModeOption(
                selected = useLastMatched, title = "Last matched",
                detail = "Uses the category you last gave a payment this rule matched. " +
                    "Changing a payment's category updates it.",
            ) { useLastMatched = true }

            if (useLastMatched) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (categoryId == 0L) "Nothing learned yet: you'll be asked the next time this rule matches."
                        else "Current category",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (categoryId != 0L) TextButton(onClick = { categoryId = 0L }) { Text("Clear (ask me next time)") }
                }
            }
            CategoryGrid(
                categories.filter { !it.isSystem }.sortedByDescending { usage[it.id] ?: 0 },
                categoryId, onSelect = { categoryId = it.id },
            )

            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                supportingText = { Text("Detected payments this rule matches get this note.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Test")
            OutlinedTextField(testSeller, { testSeller = it }, label = { Text("Seller name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            if (testSeller.isNotBlank()) {
                val money = LocalMoneyColors.current
                val matches = SellerMatcher.matches(draft, testSeller)
                val winner = SellerMatcher.firstMatch(withDraft, testSeller)
                val (text, color) = when {
                    !matches -> "✗ Doesn't match" to MaterialTheme.colorScheme.onSurfaceVariant
                    winner?.id == draft.id -> "✓ Matches" to money.income
                    else -> "Matches, but the rule \"${winner?.pattern}\" is higher in the list and wins" to
                        MaterialTheme.colorScheme.error
                }
                Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            SectionHeader("Past payments")
            if (governed.isEmpty()) {
                Text("No detected payments so far are categorized by this rule.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val sellers = governed.mapNotNull { it.merchant?.trim() }.distinctBy { it.lowercase() }
                Text(
                    "This rule decides the category of ${governed.size} past payment${if (governed.size == 1) "" else "s"} " +
                        "from: " + sellers.take(6).joinToString(", ") + if (sellers.size > 6) " and ${sellers.size - 6} more" else "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(onClick = { applyDialog = true }, enabled = canSave && categoryId != 0L) {
                    Text("Apply to past payments…")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (applyDialog) {
        val catName = categories.firstOrNull { it.id == categoryId }?.name.orEmpty()
        val uncategorized = governed.filter { it.categoryId == uncategorizedId }
        val differing = governed.filter { it.categoryId != categoryId }
        AlertDialog(
            onDismissRequest = { applyDialog = false },
            title = { Text("Set past payments to $catName?") },
            text = {
                Text(
                    "${plural(differing.size, "payment")} decided by this rule currently " +
                        (if (differing.size == 1) "has" else "have") + " another category" +
                        (if (uncategorized.isNotEmpty()) " (${uncategorized.size} Uncategorized)" else "") +
                        ". " + (if (draft.note != null) "Payments without a note also get \"${draft.note}\". " else "") +
                        "The rule is saved as well."
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        applyDialog = false
                        save { vm.setCategory(differing.map { it.id }, categoryId, draft.note) }
                    }, enabled = differing.isNotEmpty()) { Text("Change all ${differing.size}") }
                    TextButton(onClick = {
                        applyDialog = false
                        save { vm.setCategory(uncategorized.map { it.id }, categoryId, draft.note) }
                    }, enabled = uncategorized.isNotEmpty()) { Text("Only the ${uncategorized.size} uncategorized") }
                    TextButton(onClick = { applyDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this rule?") },
            text = { Text("Past payments keep their categories. New payments from these sellers will be asked again.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSellerRule(ruleId); confirmDelete = false; onDone() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

@Composable
private fun ModeOption(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
