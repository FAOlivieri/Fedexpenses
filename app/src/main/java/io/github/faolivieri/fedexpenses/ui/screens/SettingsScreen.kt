package io.github.faolivieri.fedexpenses.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import io.github.faolivieri.fedexpenses.ui.components.ColorPickerDialog
import io.github.faolivieri.fedexpenses.ui.theme.Palettes
import io.github.faolivieri.fedexpenses.ui.theme.Backgrounds
import io.github.faolivieri.fedexpenses.ui.theme.ThemeChoice
import io.github.faolivieri.fedexpenses.ui.theme.schemeFor
import io.github.faolivieri.fedexpenses.ui.theme.schemeFromSeed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.faolivieri.fedexpenses.notify.PaymentListenerService
import io.github.faolivieri.fedexpenses.ui.SettingsViewModel
import io.github.faolivieri.fedexpenses.ui.theme.LocalMoneyColors
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onEditRule: (Long) -> Unit, onAccounts: () -> Unit) {
    val context = LocalContext.current
    val rules by vm.rules.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var pickingColor by remember { mutableStateOf(false) }
    var pickingBackground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importCsv) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val restoreJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::chooseBackupFile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // --- Payment detection --------------------------------------------------------------
            SectionTitle("Payment detection")
            // Statuses are re-read whenever the screen resumes (e.g. back from system settings).
            val access = remember(resumeTick) { PaymentListenerService.hasAccess(context) }
            val listening = remember(resumeTick) { PaymentListenerService.connected }
            StatusRow(
                ok = access,
                title = "Notification access",
                detail = if (access) {
                    if (listening) "Granted · listening" else "Granted · starting"
                } else "Needed to read payment notifications",
                action = if (access) null else "Grant" to { openListenerSettings(context) },
            )
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = remember(resumeTick) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                }
                StatusRow(
                    ok = granted,
                    title = "Show notifications",
                    detail = if (granted) "Allowed" else "Needed for \"Transaction added\" and category buttons",
                    action = if (granted) null else "Allow" to { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            val pm = context.getSystemService(PowerManager::class.java)
            val unrestricted = remember(resumeTick) { pm.isIgnoringBatteryOptimizations(context.packageName) }
            StatusRow(
                ok = unrestricted,
                title = "Background activity",
                detail = if (unrestricted) "Unrestricted · Android won't stop the listener"
                else "Battery optimization may stop detection",
                action = if (unrestricted) null else "Allow" to { requestUnrestricted(context) },
            )
            Text(
                "If Android blocks notification access with \"Restricted setting\", open App info → ⋮ → " +
                    "Allow restricted settings, then grant again. On Samsung/Xiaomi also enable autostart " +
                    "and set battery to Unrestricted.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SectionTitle("Watched apps")
            rules.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEditRule(r.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        val directions = listOfNotNull(
                            "expenses".takeIf { r.expenseEnabled }, "income".takeIf { r.incomeEnabled },
                        ).joinToString(" & ").ifEmpty { "nothing enabled" }
                        Text((vm.appLabel(r.packageName) ?: "${r.packageName} (not installed)") + " · " + directions,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(r.enabled, { vm.setRuleEnabled(r, it) })
                }
            }
            TextButton(onClick = { onEditRule(0) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add app to watch")
            }

            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // --- Backup -------------------------------------------------------------------------
            SectionTitle("Google Drive backup")
            Text(
                "Android Auto Backup is on: the system backs the app's data up to your Google account " +
                    "and restores it when you reinstall or move to a new phone.\n\n" +
                    "For a backup file you control, create one below and pick Google Drive as the location. " +
                    "The app overwrites it with a fresh copy every day.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val s = state
            if (s != null) {
                if (s.backupUri != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.backupName ?: "Backup file", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    s.lastBackupError != null -> "Last attempt failed: ${s.lastBackupError}"
                                    s.lastBackupAt > 0 -> "Last backup " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(s.lastBackupAt))
                                    else -> "Not backed up yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (s.lastBackupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Daily", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(s.autoBackup, vm::setAutoBackup)
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { chooseBackup.launch("fedexpenses-backup.json") }) {
                        Text(if (s.backupUri == null) "Choose backup file" else "Change file")
                    }
                    if (s.backupUri != null) OutlinedButton(onClick = vm::backupNow) { Text("Back up now") }
                }
            }
            TextButton(onClick = { restoreJson.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                modifier = Modifier.padding(horizontal = 8.dp)) { Text("Restore from backup file…") }

            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // --- Import / export ----------------------------------------------------------------
            SectionTitle("Import / export")
            ActionRow("Import CSV", "Date, Category, Amount, Note, Account — re-importing skips duplicates") {
                importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
            }
            ActionRow("Export CSV", "Same format, opens in any spreadsheet") {
                exportCsv.launch("Transactions ${LocalDate.now()}.csv")
            }
            ActionRow("Export full backup (JSON)", "Includes categories, colors, rules and learned sellers") {
                exportJson.launch("fedexpenses-backup-${LocalDate.now()}.json")
            }

            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SectionTitle("General")
            ActionRow("Accounts", "Names, colors and balances", onAccounts)
            if (s != null) {
                var currency by remember(s.currency) { mutableStateOf(s.currency) }
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.take(4); if (it.isNotBlank()) vm.setCurrency(it.trim().take(4)) },
                    label = { Text("Currency symbol") }, singleLine = true,
                    modifier = Modifier.padding(horizontal = 16.dp).width(160.dp),
                )
                var nickname by remember(s.nickname) { mutableStateOf(s.nickname) }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it; vm.setNickname(it.trim()) },
                    label = { Text("Your name when sharing") }, singleLine = true,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(),
                )

                SectionTitle("Color palette")
                PaletteChooser(
                    selected = s.palette, customColor = s.customColor,
                    onSelect = vm::setPalette, onCustom = { pickingColor = true },
                )
                if (pickingColor) {
                    ColorPickerDialog(
                        initial = s.customColor,
                        onDismiss = { pickingColor = false },
                        onPick = { vm.setCustomColor(it); pickingColor = false },
                    )
                }

                SectionTitle("Background")
                BackgroundChooser(
                    selected = s.background, choice = ThemeChoice(s.palette, s.customColor, s.background, s.backgroundColor),
                    onSelect = vm::setBackground, onCustom = { pickingBackground = true },
                )
                if (pickingBackground) {
                    ColorPickerDialog(
                        initial = s.backgroundColor, title = "Background color",
                        onDismiss = { pickingBackground = false },
                        onPick = { vm.setBackgroundColor(it); pickingBackground = false },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.message.value = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { vm.message.value = null }) { Text("OK") } },
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Replace all data?") },
            text = { Text("Everything in the app will be replaced by the backup's contents.") },
            confirmButton = {
                TextButton(onClick = { vm.restoreBackup(uri); confirmRestore = null }) {
                    Text("Restore", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

/** Preset palettes, the wallpaper colors (Android 12+), and a custom color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteChooser(selected: String, customColor: Int, onSelect: (String) -> Unit, onCustom: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Palettes.presets.forEach { p ->
            val scheme = remember(p, dark) { schemeFromSeed(p.seed, dark) }
            PaletteSwatch(p.name, scheme.primary, scheme.primaryContainer, selected == p.id) { onSelect(p.id) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wall = remember(dark) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
            PaletteSwatch("Wallpaper", wall.primary, wall.primaryContainer, selected == Palettes.WALLPAPER,
                icon = Icons.Filled.Wallpaper) { onSelect(Palettes.WALLPAPER) }
        }
        val custom = remember(customColor, dark) { schemeFromSeed(Color(customColor), dark) }
        PaletteSwatch("Custom", custom.primary, custom.primaryContainer, selected == Palettes.CUSTOM,
            icon = Icons.Filled.Colorize, onClick = onCustom)
    }
    Text(
        "Custom: tap to pick any color. The app adjusts its lightness so text stays readable.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Background options, each previewed with its page and card colors for the current palette. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundChooser(selected: String, choice: ThemeChoice, onSelect: (String) -> Unit, onCustom: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val wallpaper = remember(dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            Triple(Backgrounds.PALETTE, "Match palette", null),
            Triple(Backgrounds.NEUTRAL, "Neutral", null),
            Triple(Backgrounds.CUSTOM, "Custom", Icons.Filled.Colorize),
        ).forEach { (id, name, icon) ->
            val preview = remember(choice, id, dark) { schemeFor(choice.copy(background = id), dark, wallpaper) }
            PaletteSwatch(name, preview.surfaceContainerHigh, preview.background, selected == id, icon = icon,
                onClick = if (id == Backgrounds.CUSTOM) onCustom else ({ onSelect(id) }))
        }
    }
    Text(
        "Applies to the page, cards, bars and dialogs. Custom: any color; the app keeps it light in light mode " +
            "and dark in dark mode so text stays readable.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A circle split into the palette's main color and its light container color. */
@Composable
private fun PaletteSwatch(
    name: String, main: Color, container: Color, selected: Boolean,
    icon: ImageVector? = null, onClick: () -> Unit,
) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(0.5f to main, 0.5f to container)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected -> Icon(Icons.Filled.Check, null, tint = if (main.luminance() > 0.4f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp))
                icon != null -> Icon(icon, null, tint = if (main.luminance() > 0.4f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, detail: String, action: Pair<String, () -> Unit>?) {
    val money = LocalMoneyColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
            tint = if (ok) money.income else MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun ActionRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openListenerSettings(context: Context) {
    val component = ComponentName(context, PaymentListenerService::class.java)
    val detail = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else null
    try {
        context.startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

@SuppressLint("BatteryLife") // Personal sideloaded app: the direct exemption prompt is what we want.
private fun requestUnrestricted(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        )
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
