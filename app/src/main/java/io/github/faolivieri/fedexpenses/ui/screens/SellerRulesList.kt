package io.github.faolivieri.fedexpenses.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.data.Category
import io.github.faolivieri.fedexpenses.data.MatchType
import io.github.faolivieri.fedexpenses.data.SellerRule
import io.github.faolivieri.fedexpenses.ui.MainViewModel
import io.github.faolivieri.fedexpenses.ui.components.EmptyState
import io.github.faolivieri.fedexpenses.ui.components.IconBadge
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val MatchType.label: String
    get() = when (this) {
        MatchType.EXACT -> "Exact match"
        MatchType.CONTAINS -> "Contains word"
        MatchType.REGEX -> "Regex"
    }

/** Seller → category rules in priority order; drag the handle to reorder. */
@Composable
fun SellerRulesList(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val rules by vm.sellerRules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val byId = remember(categories) { categories.associateBy { it.id } }

    // Local copy so dragging is instant; the new order is saved when the drag ends.
    var ordered by remember { mutableStateOf(rules) }
    LaunchedEffect(rules) { ordered = rules }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = ordered.indexOfFirst { it.id == from.key }
        val toIdx = ordered.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) ordered = ordered.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
        item(key = "header") {
            Text(
                "Detected payments get their category from the first rule that matches the seller. " +
                    "Drag ≡ to change priority.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (ordered.isEmpty()) item(key = "empty") {
            EmptyState("No seller rules yet. One is created whenever you categorize a payment from a new seller, or add your own with +.")
        }
        items(ordered, key = { it.id }) { rule ->
            ReorderableItem(reorderState, key = rule.id) { dragging ->
                val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "drag")
                Surface(shadowElevation = elevation, color = MaterialTheme.colorScheme.surface) {
                    Column {
                        SellerRuleRow(
                            rule, rule.categoryId?.let(byId::get), onClick = { onOpen(rule.id) },
                            handle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStopped = { vm.reorderSellerRules(ordered.map { it.id }) },
                                    ),
                                ) { Icon(Icons.Filled.DragHandle, "Drag to reorder") }
                            },
                        )
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerRuleRow(rule: SellerRule, category: Category?, onClick: () -> Unit, handle: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        handle()
        Column(Modifier.weight(1f)) {
            Text(
                rule.pattern, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = if (rule.matchType == MatchType.REGEX) FontFamily.Monospace else null,
            )
            Text(
                rule.matchType.label + (if (rule.useLastMatched) " · last matched" else "") +
                    (rule.note?.let { " · note \"$it\"" } ?: ""),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (category != null) {
                IconBadge(category.icon, category.color, 28.dp)
                Text(category.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            } else {
                Text("Ask me", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
