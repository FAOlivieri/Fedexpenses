package io.github.faolivieri.fedexpenses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.faolivieri.fedexpenses.data.Account
import io.github.faolivieri.fedexpenses.data.Category
import io.github.faolivieri.fedexpenses.data.Defaults
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.CategoryIcons
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.Period
import io.github.faolivieri.fedexpenses.util.PeriodType

/** Colored circle with the category's icon. */
@Composable
fun IconBadge(icon: String, color: Int, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val bg = Color(color)
    Box(
        modifier.size(size).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.get(icon), contentDescription = null,
            tint = if (bg.luminance() > 0.6f) Color.Black else Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun CategoryBadge(c: Category, size: Dp = 40.dp) = IconBadge(c.icon, c.color, size)

@Composable
fun moneyColor(type: TxType): Color =
    if (type == TxType.EXPENSE) LocalMoneyColors.current.expense else LocalMoneyColors.current.income

@Composable
fun SignedAmount(cents: Long, type: TxType, modifier: Modifier = Modifier, bold: Boolean = true,
                 style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(
        (if (type == TxType.EXPENSE) "−" else "+") + Money.format(cents),
        color = moneyColor(type), style = style,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeriodSelector(period: Period, onChange: (Period) -> Unit, modifier: Modifier = Modifier) {
    // With large text the date navigator moves below the Month/Year toggle instead of being squeezed.
    FlowRow(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
        // Sized by its labels, which never wrap, so large font settings stay on one line.
        SingleChoiceSegmentedButtonRow(Modifier.width(IntrinsicSize.Max).align(Alignment.CenterVertically)) {
            PeriodType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = period.type == t,
                    onClick = { onChange(period.withType(t)) },
                    shape = SegmentedButtonDefaults.itemShape(i, PeriodType.entries.size),
                    icon = {},
                ) {
                    Text(if (t == PeriodType.MONTH) "Month" else "Year", style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, softWrap = false)
                }
            }
        }
        Row(Modifier.weight(1f).align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(period.shift(-1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
            }
            // Falls back to "Sep 2026" when "September 2026" doesn't fit.
            var short by remember(period) { mutableStateOf(false) }
            Text(
                if (short) period.shortLabel else period.label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { if (it.hasVisualOverflow && !short) short = true },
                modifier = Modifier.weight(1f).clickable { onChange(Period.current(period.type)) },
            )
            IconButton(onClick = { onChange(period.shift(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val custom = selected !in Defaults.palette
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Defaults.palette.forEach { c -> ColorDot(Color(c), c == selected) { onSelect(c) } }
        // Last dot: any color. Shows the current color when it isn't one of the presets.
        Box(
            Modifier.size(36.dp)
                .background(if (custom) Color(selected) else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .then(if (custom) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .clickable { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (custom) Icons.Filled.Check else Icons.Filled.Colorize, "Custom color",
                tint = when {
                    !custom -> MaterialTheme.colorScheme.onSurfaceVariant
                    Color(selected).luminance() > 0.6f -> Color.Black
                    else -> Color.White
                },
                modifier = Modifier.size(18.dp))
        }
    }
    if (picking) {
        ColorPickerDialog(initial = selected, onDismiss = { picking = false }, onPick = { onSelect(it); picking = false })
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp)
            .background(color, CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > 0.6f) Color.Black else Color.White,
            modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, color: Int, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CategoryIcons.all.forEach { (key, vector) ->
            val isSel = key == selected
            Box(
                Modifier.size(40.dp)
                    .background(if (isSel) Color(color) else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(vector, key, tint = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeToggle(type: TxType, onChange: (TxType) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        listOf(TxType.EXPENSE, TxType.INCOME).forEachIndexed { i, t ->
            SegmentedButton(
                selected = type == t, enabled = enabled,
                onClick = { onChange(t) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(if (t == TxType.EXPENSE) "Expense" else "Income") }
        }
    }
}

/** Create or edit a category. [initial] null creates a new one of [defaultType]. */
@Composable
fun CategoryEditorDialog(
    initial: Category?,
    defaultType: TxType,
    suggestedColorIndex: Int,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: defaultType) }
    var color by remember { mutableIntStateOf(initial?.color ?: Defaults.colorFor(suggestedColorIndex)) }
    var icon by remember { mutableStateOf(initial?.icon ?: "category") }
    var iconTouched by remember { mutableStateOf(initial != null) }
    val system = initial?.isSystem == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon, color, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (!iconTouched) icon = Defaults.suggestIcon(it)
                        },
                        label = { Text("Name") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                if (!system) TypeToggle(type, { type = it }, modifier = Modifier.fillMaxWidth())
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                IconPicker(icon, color) { icon = it; iconTouched = true }
                if (onDelete != null && !system) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDelete) {
                        Text("Delete category", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave((initial ?: Category(name = "", type = type, color = color, icon = icon))
                    .copy(name = name.trim(), type = type, color = color, icon = icon))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AccountEditorDialog(
    initial: Account?,
    suggestedColorIndex: Int,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: Defaults.colorFor(suggestedColorIndex)) }
    var icon by remember { mutableStateOf(initial?.icon ?: "wallet") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon, color, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                IconPicker(icon, color) { icon = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave((initial ?: Account(name = "", color = color)).copy(name = name.trim(), color = color, icon = icon))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Single-line text that shrinks its font (down to [minScale]) instead of wrapping or cutting off. */
@Composable
fun FitText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier,
            fontWeight: FontWeight? = null, color: Color = Color.Unspecified, minScale: Float = 0.5f) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    Text(
        text, style = style.copy(fontSize = style.fontSize * scale), fontWeight = fontWeight, color = color,
        maxLines = 1, softWrap = false, modifier = modifier,
        onTextLayout = { if (it.hasVisualOverflow && scale > minScale) scale *= 0.9f },
    )
}
