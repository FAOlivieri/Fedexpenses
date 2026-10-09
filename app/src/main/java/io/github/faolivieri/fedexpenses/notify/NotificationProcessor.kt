package io.github.faolivieri.fedexpenses.notify

import io.github.faolivieri.fedexpenses.data.CapturedNotification
import io.github.faolivieri.fedexpenses.data.NotificationRule
import io.github.faolivieri.fedexpenses.data.ProcessedNotification
import io.github.faolivieri.fedexpenses.data.Repository
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxSource
import io.github.faolivieri.fedexpenses.data.TxType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Plain-data snapshot of a posted notification; the framework objects are not kept around. */
data class IncomingNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** Android 15+ replaced the content because it looked like a one-time code (see README). */
    val hidden: Boolean = false,
)

class NotificationProcessor(
    private val repo: Repository,
    private val notifier: TxNotifier,
    private val scope: CoroutineScope,
) {
    /** Every watched app, including switched-off ones: their notifications are still captured. */
    private val rules: StateFlow<List<NotificationRule>?> =
        repo.rules.observeAll().stateIn(scope, SharingStarted.Eagerly, null)

    /** Serializes processing so the "already processed" check can't race. */
    private val lock = Mutex()

    init {
        // The listener can start before the UI has ever run (e.g. after a reboot).
        scope.launch { repo.ensureSeeded() }
    }

    /**
     * Cheap check on the listener's binder thread: notifications from apps no rule watches are
     * dropped without touching the database. Before rules load, everything passes through.
     */
    fun isWatched(packageName: String): Boolean =
        rules.value?.any { it.packageName == packageName } ?: true

    fun submit(n: IncomingNotification) {
        scope.launch { process(n) }
    }

    /** After a (re)connect: catch up on everything still in the shade, then tidy old records. */
    fun catchUp(active: List<IncomingNotification>) {
        scope.launch {
            active.forEach { process(it) }
            val month = 30L * 24 * 3600 * 1000
            repo.notificationLog.pruneProcessed(System.currentTimeMillis() - month)
        }
    }

    private suspend fun process(n: IncomingNotification) = lock.withLock {
        val watching = rules.filterNotNull().first().filter { it.packageName == n.packageName }
        if (watching.isEmpty()) return@withLock

        val log = repo.notificationLog
        val dedupKey = "${n.key}|${n.postedAt}|${(n.title + "\n" + n.text).hashCode()}"
        if (log.isProcessed(dedupKey)) return@withLock
        log.markProcessed(ProcessedNotification(dedupKey, System.currentTimeMillis()))

        val active = watching.filter { it.enabled && it.activePatterns.isNotEmpty() }
        val matched = if (n.hidden) null else match(active, n.title, n.text)
        // The real text of a hidden notification is withheld from third-party listeners; let the user add it by hand.
        if (n.hidden && active.isNotEmpty()) notifier.showHidden(n.key, active.first(), n.postedAt)
        val txId = matched?.let { (rule, type, m) -> record(rule, type, m, n.postedAt) }

        log.capture(CapturedNotification(packageName = n.packageName, title = n.title, text = n.text,
            postedAt = n.postedAt, matchedRuleId = matched?.first?.id, transactionId = txId))
        log.trimCaptured(KEEP_CAPTURED)
    }

    /**
     * Records the transaction [rule] parses from an already captured notification, whether or not
     * the rule is saved or switched on. Does nothing if the notification already has a transaction
     * or the rule doesn't match it.
     */
    suspend fun processCaptured(capturedId: Long, rule: NotificationRule) = lock.withLock {
        val log = repo.notificationLog
        val c = log.getCaptured(capturedId)?.takeIf { it.transactionId == null } ?: return@withLock
        val (_, type, m) = match(listOf(rule), c.title, c.text) ?: return@withLock
        val txId = record(rule, type, m, c.postedAt)
        log.updateCaptured(c.copy(matchedRuleId = rule.id.takeIf { it != 0L }, transactionId = txId))
    }

    /** First matching pattern wins: each app's income patterns, then its expense patterns. */
    private fun match(rules: List<NotificationRule>, title: String, text: String): Triple<NotificationRule, TxType, ParseResult.Match>? {
        for (rule in rules) {
            for ((type, pattern) in rule.activePatterns) {
                val r = RuleEngine.parse(pattern, title, text)
                if (r is ParseResult.Match) return Triple(rule, type, r)
            }
        }
        return null
    }

    /** Saves the transaction, categorized by seller rules, and notifies about it. */
    private suspend fun record(rule: NotificationRule, type: TxType, m: ParseResult.Match, timestamp: Long): Long {
        val sellerRule = repo.sellerRuleFor(m.merchant)
        val known = sellerRule?.categoryId?.let { repo.getCategory(it) }
        val categoryId = known?.id ?: repo.uncategorizedId()
        val tx = Transaction(
            timestamp = timestamp, amountCents = m.amountCents, type = type,
            categoryId = categoryId, accountId = rule.accountId,
            note = sellerRule?.note.orEmpty(), merchant = m.merchant, source = TxSource.NOTIFICATION,
        )
        val txId = repo.saveTransaction(tx)
        val category = known ?: repo.getCategory(categoryId)!!
        val picks = if (known == null) repo.mostUsedCategories(type, QUICK_PICKS) else emptyList()
        notifier.showDetected(tx.copy(id = txId), category, ask = known == null, quickPicks = picks)
        return txId
    }

    companion object {
        /** Plus "Edit manually" this hits Android's three-action limit. */
        const val QUICK_PICKS = 2
        const val KEEP_CAPTURED = 50
    }
}
