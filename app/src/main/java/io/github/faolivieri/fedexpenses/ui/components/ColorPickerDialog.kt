package io.github.faolivieri.fedexpenses.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.faolivieri.fedexpenses.data.Defaults

/** Free color choice: hue bar, saturation and brightness sliders, quick swatches and hex entry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit, title: String = "Custom color") {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, brightness)
    var hex by remember { mutableStateOf(toHex(color.toArgb())) }

    fun set(argb: Int) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, out)
        hue = out[0]; saturation = out[1]; brightness = out[2]
        hex = toHex(argb)
    }
    fun syncHex() { hex = toHex(Color.hsv(hue, saturation, brightness).toArgb()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(color, RoundedCornerShape(12.dp)))

                Text("Hue", style = MaterialTheme.typography.labelLarge)
                HueBar(hue) { hue = it; syncHex() }

                Text("Saturation", style = MaterialTheme.typography.labelLarge)
                Slider(saturation, { saturation = it; syncHex() })
                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(brightness, { brightness = it; syncHex() })

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Defaults.palette.forEach { c ->
                        Box(Modifier.size(28.dp).background(Color(c), CircleShape).clickable { set(c) })
                    }
                }

                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.take(7)
                        parseHex(hex)?.let { argb ->
                            val out = FloatArray(3)
                            android.graphics.Color.colorToHSV(argb, out)
                            hue = out[0]; saturation = out[1]; brightness = out[2]
                        }
                    },
                    label = { Text("Hex") }, singleLine = true,
                    isError = parseHex(hex) == null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.width(160.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color.toArgb()) }) { Text("Use this color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Rainbow bar; tap or drag to pick the hue (0..360). Built from plain colored blocks rather than a
 * gradient shader, which some software renderers (e.g. the Android emulator's) crash on.
 */
@Composable
private fun HueBar(hue: Float, onHue: (Float) -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp)
            .pointerInput(Unit) { detectTapGestures { onHue((it.x / size.width).coerceIn(0f, 1f) * 360f) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onHue((change.position.x / size.width).coerceIn(0f, 1f) * 360f) }
            },
    ) {
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
            repeat(HUE_STEPS) { i ->
                Box(Modifier.weight(1f).fillMaxHeight().background(Color.hsv(i * 360f / HUE_STEPS, 1f, 1f)))
            }
        }
        val thumb = 26.dp
        val x = (maxWidth - thumb) * (hue / 360f)
        Box(
            Modifier.offset(x = x).align(Alignment.CenterStart).size(thumb)
                .border(3.dp, ring, CircleShape).padding(3.dp)
                .background(Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f), CircleShape),
        )
    }
}

private const val HUE_STEPS = 36

private fun toHex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

private fun parseHex(s: String): Int? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6 || t.any { it.uppercaseChar() !in "0123456789ABCDEF" }) return null
    return (0xFF000000 or t.toLong(16)).toInt()
}

