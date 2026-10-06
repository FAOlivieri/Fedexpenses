package io.github.faolivieri.fedexpenses.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.faolivieri.fedexpenses.data.Category

/** Four-column grid of category tiles, optionally ending in a "New" tile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Category) -> Unit,
    onNew: (() -> Unit)? = null,
) {
    val tiles = categories.size + if (onNew != null) 1 else 0
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4, modifier = Modifier.fillMaxWidth(),
    ) {
        categories.forEach { c -> CategoryTile(c, c.id == selectedId, Modifier.weight(1f)) { onSelect(c) } }
        if (onNew != null) NewTile("New", Modifier.weight(1f), onNew)
        // Pad the last row so tiles keep equal widths.
        repeat((4 - tiles % 4) % 4) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun CategoryTile(c: Category, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
            ),
            contentAlignment = Alignment.Center,
        ) { IconBadge(c.icon, c.color, 42.dp) }
        Spacer(Modifier.height(4.dp))
        Text(c.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun NewTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
