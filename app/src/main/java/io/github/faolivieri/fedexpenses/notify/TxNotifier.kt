package io.github.faolivieri.fedexpenses.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.faolivieri.fedexpenses.MainActivity
import io.github.faolivieri.fedexpenses.R
import io.github.faolivieri.fedexpenses.data.Category
import io.github.faolivieri.fedexpenses.data.NotificationRule
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.util.Money

/** Posts the "Transaction added" / "What category is this?" notifications. */
class TxNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ASK, "Uncategorized payments", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Payments from new sellers that need a category" },
                NotificationChannel(CHANNEL_ADDED, "Detected payments", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Payments categorized automatically" },
            )
        )
    }

    private fun canPost(): Boolean =
        nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /**
     * [ask] means the seller is unknown: ask for a category, with one button per quick pick.
     * Android shows at most three action buttons, so callers pass up to two picks plus "Edit manually".
     */
    @SuppressLint("MissingPermission")
    fun showDetected(tx: Transaction, category: Category, ask: Boolean, quickPicks: List<Category>) {
        if (!canPost()) return
        val amount = (if (tx.type == TxType.EXPENSE) "−" else "+") + Money.format(tx.amountCents)
        val seller = tx.merchant ?: tx.note
        val b = NotificationCompat.Builder(context, if (ask) CHANNEL_ASK else CHANNEL_ADDED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (ask) "What category is this?" else "Transaction detected")
            .setContentText(if (ask) "$amount · $seller" else "$amount · $seller · ${category.name}")
            .setColor(category.color)
            .setWhen(tx.timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(editIntent(tx.id))
        for (c in quickPicks) {
            b.addAction(0, c.name, categoryIntent(tx.id, c.id))
        }
        b.addAction(0, "Edit manually", editIntent(tx.id))
        nm.notify(TAG, notificationId(tx.id), b.build())
    }

    /** Replaces the question with a silent confirmation once a quick pick was tapped. */
    @SuppressLint("MissingPermission")
    fun showCategorized(tx: Transaction, category: Category) {
        if (!canPost()) return
        val seller = tx.merchant ?: tx.note
        val n = NotificationCompat.Builder(context, CHANNEL_ADDED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Saved as ${category.name}")
            .setContentText(Money.formatSigned(tx.signedCents) + " · $seller")
            .setColor(category.color)
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .setContentIntent(editIntent(tx.id))
            .addAction(0, "Edit manually", editIntent(tx.id))
            .build()
        nm.notify(TAG, notificationId(tx.id), n)
    }

    /** A watched app posted something Android hid from us: offer to enter it manually. */
    @SuppressLint("MissingPermission")
    fun showHidden(key: String, rule: NotificationRule, postedAt: Long) {
        if (!canPost()) return
        val i = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEW_TX)
            // Unknown direction: assume an expense unless the app only reports income.
            .putExtra(MainActivity.EXTRA_TYPE, (if (rule.incomeEnabled && !rule.expenseEnabled) TxType.INCOME else TxType.EXPENSE).name)
            .putExtra(MainActivity.EXTRA_TIMESTAMP, postedAt)
            .putExtra(MainActivity.EXTRA_ACCOUNT_ID, rule.accountId)
            .setData(Uri.parse("expenses://hidden/${key.hashCode()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, key.hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_ASK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${rule.name}: payment detected")
            .setContentText("Android hid the details. Tap to enter the amount.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Android hid this notification's content because it looked like a one-time code. Tap to enter the amount."))
            .setWhen(postedAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(0, "Add manually", pi)
            .build()
        nm.notify(TAG_HIDDEN, key.hashCode(), n)
    }

    fun cancel(txId: Long) = nm.cancel(TAG, notificationId(txId))

    private fun editIntent(txId: Long): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_EDIT_TX)
            .putExtra(MainActivity.EXTRA_TX_ID, txId)
            .setData(Uri.parse("expenses://tx/$txId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, notificationId(txId), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun categoryIntent(txId: Long, categoryId: Long): PendingIntent {
        val i = Intent(context, CategoryActionReceiver::class.java)
            .setAction(CategoryActionReceiver.ACTION_SET_CATEGORY)
            .setData(Uri.parse("expenses://tx/$txId/category/$categoryId"))
            .putExtra(CategoryActionReceiver.EXTRA_TX_ID, txId)
            .putExtra(CategoryActionReceiver.EXTRA_CATEGORY_ID, categoryId)
        return PendingIntent.getBroadcast(context, 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun notificationId(txId: Long): Int = (txId % Int.MAX_VALUE).toInt()

    companion object {
        const val CHANNEL_ASK = "tx_ask"
        const val CHANNEL_ADDED = "tx_added"
        const val TAG = "tx"
        const val TAG_HIDDEN = "hidden"
    }
}
