package io.github.faolivieri.fedexpenses.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import io.github.faolivieri.fedexpenses.container

/**
 * Bound and kept alive by the system for as long as notification access is granted, so no
 * foreground service is needed. Work per notification is a set lookup unless the posting app
 * is one the user configured a rule for.
 */
class PaymentListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        // If the process died, payments may have arrived meanwhile: record what's still showing.
        val active = try {
            activeNotifications?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        val processor = container.processor
        processor.catchUp(active.mapNotNull { snapshot(it) })
    }

    override fun onListenerDisconnected() {
        connected = false
        requestRebind(ComponentName(this, PaymentListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val processor = container.processor
        if (!processor.isWatched(sbn.packageName)) return
        snapshot(sbn)?.let(processor::submit)
    }

    private fun snapshot(sbn: StatusBarNotification): IncomingNotification? {
        val n = sbn.notification ?: return null
        if (sbn.packageName == packageName) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return IncomingNotification(sbn.key, sbn.packageName, title, text, eventTime(n.`when`, sbn.postTime),
            hidden = text == redactedText)
    }

    /** The placeholder Android 15+ shows untrusted listeners instead of OTP-like notifications. */
    private val redactedText: String? by lazy {
        val res = Resources.getSystem()
        val id = res.getIdentifier("redacted_notification_message", "string", "android")
        if (id != 0) res.getString(id) else null
    }

    /** Prefer the app's own event time; fall back to post time when it looks unreliable. */
    private fun eventTime(whenMillis: Long, postTime: Long): Long {
        val now = System.currentTimeMillis()
        val day = 24L * 3600 * 1000
        return if (whenMillis in (now - 30 * day)..(now + day)) whenMillis else postTime
    }

    companion object {
        @Volatile
        var connected = false
            private set

        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Asks the system to rebind the listener if access is granted but it isn't running. */
        fun ensureBound(context: Context) {
            if (hasAccess(context) && !connected) {
                requestRebind(ComponentName(context, PaymentListenerService::class.java))
            }
        }
    }
}
