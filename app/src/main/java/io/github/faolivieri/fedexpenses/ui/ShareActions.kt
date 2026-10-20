package io.github.faolivieri.fedexpenses.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.github.faolivieri.fedexpenses.data.SharePayload
import io.github.faolivieri.fedexpenses.data.Shares
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.toLocalDate
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/** Sending shared expenses through the Android share sheet, and reading what other phones sent. */
object ShareActions {
    private const val MAX_FILE_BYTES = 1 shl 20

    /** Writes [p] to a file and shares it. */
    fun sendFile(context: Context, p: SharePayload, recipient: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Fedexpenses ${fileSafe(p.fromName)} to ${fileSafe(recipient)} ${LocalDate.now()}.json")
        file.writeText(Shares.toJson(p))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val item = p.items.singleOrNull()
        val text = if (item != null) {
            val what = item.seller?.trim()?.takeIf { it.isNotEmpty() } ?: item.note.trim().ifEmpty { "an expense" }
            "${p.fromName} shared an expense with you through the Fedexpenses app: $what, " +
                "${Dates.short(item.timestamp.toLocalDate())}. The total was ${Money.format(item.totalCents)} and your " +
                "part is ${Money.format(item.recipientCents)}. Open the attached file with Fedexpenses to add it."
        } else {
            "${p.fromName} shared ${p.items.size} expenses with you through the Fedexpenses app " +
                "(your part: ${Money.format(p.items.sumOf { it.recipientCents })}). " +
                "Open the attached file with Fedexpenses to add them."
        }
        val send = Intent(Intent.ACTION_SEND).setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, "Share expenses"))
    }

    /** The share file an intent carries; null when the intent carries no file. */
    fun fromIntent(context: Context, intent: Intent): Result<SharePayload>? {
        @Suppress("DEPRECATION")
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return null
        return read(context, uri)
    }

    /** Reads a share file. */
    fun read(context: Context, uri: Uri): Result<SharePayload> = runCatching {
        val out = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= MAX_FILE_BYTES) { "Not a Fedexpenses share" }
            }
        }
        Shares.fromJson(out.toString(Charsets.UTF_8.name()))
    }

    private fun fileSafe(s: String) = s.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "someone" }
}

/**
 * Returns a function that runs a share action, first asking for your sharing name if it isn't set.
 * Call it from a composable and keep the result.
 */
@Composable
fun rememberNicknameGate(vm: MainViewModel): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { action ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true,
                    label = { Text("Shown to the people you share with") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.setMyNickname(name.trim())
                    pending = null
                    action()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
    return { action -> if (vm.myNickname().isBlank()) pending = action else action() }
}
