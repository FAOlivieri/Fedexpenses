package io.github.faolivieri.fedexpenses.notify

import io.github.faolivieri.fedexpenses.data.CapturedNotification
import io.github.faolivieri.fedexpenses.data.NotificationRule
import io.github.faolivieri.fedexpenses.data.ProcessedNotification
import io.github.faolivieri.fedexpenses.data.Repository
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val rules: StateFlow<List<NotificationRule>?> =
        repo.rules.observeAll().map { list -> list.filter { it.enabled } }
            .stateIn(scope, SharingStarted.Eagerly, null)

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

        if (n.hidden) {
            // The real text is withheld from third-party listeners; let the user add it by hand.
            notifier.showHidden(n.key, watching.first(), n.postedAt)
            log.capture(CapturedNotification(packageName = n.packageName, title = n.title, text = n.text,
                postedAt = n.postedAt, matchedRuleId = null, transactionId = null))
            log.trimCaptured(KEEP_CAPTURED)
            return@withLock
        }

        var matched: Pair<NotificationRule, ParseResult.Match>? = null
        for (rule in watching) {
            val r = RuleEngine.parse(rule.pattern, n.title, n.text)
            if (r is ParseResult.Match) { matched = rule to r; break }
        }

        var txId: Long? = null
        if (matched != null) {
            val (rule, m) = matched
            val known = repo.categoryForMerchant(m.merchant)
            val categoryId = known?.id ?: repo.uncategorizedId()
            val tx = Transaction(
                timestamp = n.postedAt, amountCents = m.amountCents, type = rule.type,
                categoryId = categoryId, accountId = rule.accountId,
                merchant = m.merchant, source = TxSource.NOTIFICATION,
            )
            txId = repo.saveTransaction(tx)
            val saved = tx.copy(id = txId)
            val category = known ?: repo.getCategory(categoryId)!!
            val picks = if (known == null) repo.mostUsedCategories(rule.type, QUICK_PICKS) else emptyList()
            notifier.showDetected(saved, category, picks)
        }

        log.capture(CapturedNotification(packageName = n.packageName, title = n.title, text = n.text,
            postedAt = n.postedAt, matchedRuleId = matched?.first?.id, transactionId = txId))
        log.trimCaptured(KEEP_CAPTURED)
    }

    companion object {
        /** Plus "Edit manually" this hits Android's three-action limit. */
        const val QUICK_PICKS = 2
        const val KEEP_CAPTURED = 50
    }
}
