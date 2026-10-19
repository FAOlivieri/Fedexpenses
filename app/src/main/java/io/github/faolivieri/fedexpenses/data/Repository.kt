package io.github.faolivieri.fedexpenses.data

import androidx.room.withTransaction
import io.github.faolivieri.fedexpenses.util.toLocalDate
import io.github.faolivieri.fedexpenses.util.toMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class ImportSummary(val added: Int, val skippedDuplicates: Int, val newCategories: Int, val errors: List<String>)

class Repository(private val db: AppDatabase) {
    private val categories = db.categories()
    private val accounts = db.accounts()
    private val txs = db.transactions()
    val sellerRules = db.sellerRules()
    private val recurrences = db.recurrences()
    private val recurringLock = Mutex()
    val rules = db.rules()
    val notificationLog = db.notificationLog()
    private val sharing = db.sharing()

    private val seedLock = Mutex()
    @Volatile private var seeded = false
    @Volatile private var uncategorizedIdCache = 0L

    /** Creates Uncategorized, a default account, starter categories and the Google Wallet rule on first run. */
    suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (seeded) return
            db.withTransaction {
                if (categories.getUncategorized() == null) {
                    categories.insert(
                        Category(name = Defaults.UNCATEGORIZED_NAME, type = TxType.EXPENSE,
                            color = Defaults.UNCATEGORIZED_COLOR, icon = "help", isSystem = true)
                    )
                    if (categories.getAll().size == 1) {
                        Defaults.categories.forEachIndexed { i, s ->
                            categories.insert(Category(name = s.name, type = s.type,
                                color = Defaults.colorFor(i), icon = Defaults.suggestIcon(s.name)))
                        }
                    }
                }
                val accountId = accounts.getAll().firstOrNull()?.id
                    ?: accounts.insert(Account(name = "Personal", color = Defaults.palette[0]))
                if (rules.getAll().isEmpty()) {
                    rules.insert(NotificationRule(name = "Google Wallet", packageName = Defaults.GOOGLE_WALLET_PACKAGE,
                        expensePattern = Defaults.GOOGLE_WALLET_PATTERN, accountId = accountId))
                }
            }
            seeded = true
        }
    }

    suspend fun uncategorizedId(): Long {
        if (uncategorizedIdCache == 0L) {
            ensureSeeded()
            uncategorizedIdCache = categories.getUncategorized()!!.id
        }
        return uncategorizedIdCache
    }

    // --- Observing ---------------------------------------------------------------------------

    fun observeCategories(): Flow<List<Category>> = categories.observeAll()
    fun observeAccounts(): Flow<List<Account>> = accounts.observeAll()
    fun observeCategoryUsage(): Flow<List<IdCount>> = categories.observeUsageCounts()
    fun observeAccountBalances(): Flow<List<IdSum>> = accounts.observeBalances()
    fun observeEarliest(): Flow<Long?> = txs.observeEarliest()

    fun observeDetails(from: Long, to: Long): Flow<List<TxDetails>> =
        combine(txs.observeRange(from, to), categories.observeAll(), accounts.observeAll(), sharing.observeSplits()) { list, cats, accs, splits ->
            val catMap = cats.associateBy { it.id }
            val accMap = accs.associateBy { it.id }
            val splitMap = splits.groupBy { it.transactionId }
            list.mapNotNull { t ->
                val c = catMap[t.categoryId] ?: return@mapNotNull null
                val a = accMap[t.accountId] ?: return@mapNotNull null
                TxDetails(t, c, a, splitMap[t.id].orEmpty())
            }
        }

    suspend fun getTransaction(id: Long): Transaction? = txs.get(id)
    suspend fun getCategory(id: Long): Category? = categories.get(id)

    // --- Transactions ------------------------------------------------------------------------

    /** [splits] replaces the transaction's split when given; empty means not split. */
    suspend fun saveTransaction(tx: Transaction, splits: List<SplitPart>? = null): Long {
        val id = db.withTransaction {
            val id = if (tx.id == 0L) txs.insert(tx) else tx.also { txs.update(it) }.id
            if (splits != null) {
                sharing.deleteSplitsFor(id)
                sharing.insertSplits(splits.map { it.copy(id = 0, transactionId = id) })
            }
            id
        }
        learn(tx.merchant, tx.categoryId)
        return id
    }

    suspend fun splitsFor(txId: Long): List<SplitPart> = sharing.splitsFor(txId)

    suspend fun deleteTransaction(id: Long) = txs.delete(id)

    /** Used by the notification quick-reply buttons. */
    suspend fun setCategory(txId: Long, categoryId: Long): Transaction? {
        val tx = txs.get(txId) ?: return null
        val updated = tx.copy(categoryId = categoryId)
        txs.update(updated)
        learn(tx.merchant, categoryId)
        return updated
    }

    /**
     * Called whenever the user categorizes a detected payment. The topmost matching rule learns
     * the category if it's a "last matched" rule; a seller no rule covers gets a new exact rule.
     */
    private suspend fun learn(merchant: String?, categoryId: Long) {
        if (merchant.isNullOrBlank() || categoryId == uncategorizedId()) return
        val now = System.currentTimeMillis()
        val rule = SellerMatcher.firstMatch(sellerRules.getAll(), merchant)
        when {
            rule == null -> sellerRules.insert(
                SellerRule(matchType = MatchType.EXACT, pattern = merchant.trim(), useLastMatched = true,
                    categoryId = categoryId, position = sellerRules.maxPosition() + 1, updatedAt = now)
            )
            rule.useLastMatched && rule.categoryId != categoryId ->
                sellerRules.update(rule.copy(categoryId = categoryId, updatedAt = now))
        }
    }

    /** The seller rule that decides [merchant]'s category and note: the topmost match, if any. */
    suspend fun sellerRuleFor(merchant: String): SellerRule? = SellerMatcher.firstMatch(sellerRules.getAll(), merchant)

    // --- Seller rules -------------------------------------------------------------------------

    fun observeSellerRules(): Flow<List<SellerRule>> = sellerRules.observeAll()
    fun observeDetectedPayments(): Flow<List<Transaction>> = txs.observeWithMerchant()

    suspend fun saveSellerRule(rule: SellerRule): Long {
        val r = rule.copy(updatedAt = System.currentTimeMillis())
        return if (r.id == 0L) sellerRules.insert(r.copy(position = sellerRules.maxPosition() + 1))
        else r.also { sellerRules.update(it) }.id
    }

    suspend fun deleteSellerRule(id: Long) = sellerRules.delete(id)

    /** Persists a new top-to-bottom order. */
    suspend fun reorderSellerRules(orderedIds: List<Long>) {
        val byId = sellerRules.getAll().associateBy { it.id }
        sellerRules.updateAll(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
    }

    /** Applies a seller rule to past payments: its category, and its note where they have none. */
    suspend fun setCategory(txIds: List<Long>, categoryId: Long, note: String? = null) {
        if (txIds.isEmpty()) return
        db.withTransaction {
            txs.setCategory(txIds, categoryId)
            if (!note.isNullOrBlank()) txs.setNoteWhereEmpty(txIds, note)
        }
    }

    suspend fun mostUsedCategories(type: TxType, limit: Int): List<Category> {
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val recent = categories.mostUsed(type, since, limit)
        if (recent.size >= limit) return recent
        val all = categories.mostUsed(type, 0, limit)
        // Little or no history yet (e.g. a fresh install): fill up with other categories of this type.
        val others = categories.getAll().filter { it.type == type && !it.isSystem }.sortedBy { it.name.lowercase() }
        return (recent + all + others).distinctBy { it.id }.take(limit)
    }

    // --- Repeating transactions ----------------------------------------------------------------

    fun observeRecurrences(): Flow<List<Recurrence>> = recurrences.observeAll()
    suspend fun getRecurrence(id: Long): Recurrence? = recurrences.get(id)
    suspend fun latestIdInSeries(recurrenceId: Long): Long? = txs.latestIdInSeries(recurrenceId)

    /** Creates every repeating transaction that is due by [today]. Safe to call any number of times. */
    suspend fun materializeRecurring(today: LocalDate = LocalDate.now()): Int = recurringLock.withLock {
        ensureSeeded()
        var created = 0
        db.withTransaction {
            for (r in recurrences.getAll()) {
                val due = Recurrences.due(r, today)
                if (due.isEmpty()) continue
                txs.insertAll(due.map { (_, day) -> r.transactionOn(day) })
                recurrences.update(r.copy(nextIndex = due.last().first + 1))
                created += due.size
            }
        }
        created
    }

    /**
     * Makes [tx] the first payment of a new series. A first payment dated in the future isn't
     * saved now: like every later one, it's created at the start of its day.
     */
    suspend fun startRecurring(tx: Transaction, frequency: Frequency, endDay: Long?) {
        val day = tx.timestamp.toLocalDate()
        db.withTransaction {
            val createNow = tx.id != 0L || day <= LocalDate.now()
            val r = Recurrence.from(tx, frequency, day, nextIndex = if (createNow) 1 else 0, endDay = endDay)
            val id = recurrences.insert(r)
            if (createNow) saveTransaction(tx.copy(recurrenceId = id))
        }
        materializeRecurring()
    }

    /**
     * "This and future": saves [tx], copies its fields to later payments of its series and to the
     * template. If the date or frequency changed, the schedule restarts from [tx]'s date.
     */
    suspend fun updateSeries(tx: Transaction, originalDay: LocalDate, frequency: Frequency, endDay: Long?) {
        val r = tx.recurrenceId?.let { recurrences.get(it) }
            ?: return startRecurring(tx.copy(recurrenceId = null), frequency, endDay)
        db.withTransaction {
            saveTransaction(tx)
            txs.updateLaterInSeries(r.id, tx.timestamp, tx.amountCents, tx.type, tx.categoryId, tx.accountId,
                tx.note, tx.merchant)
            val day = tx.timestamp.toLocalDate()
            var updated = Recurrence.from(tx, frequency, r.start, r.nextIndex, endDay).copy(id = r.id)
            if (frequency != r.frequency || day != originalDay) {
                // Restart from this payment, skipping dates the series has already produced.
                val last = maxOf(day, txs.lastInSeries(r.id)?.toLocalDate() ?: day)
                updated = updated.copy(startDay = day.toEpochDay(),
                    nextIndex = Recurrences.firstIndexAfter(frequency, day, last))
            }
            recurrences.update(updated)
        }
        materializeRecurring()
    }

    /** Ends a series; its existing payments stay. */
    suspend fun stopRecurring(recurrenceId: Long) = db.withTransaction {
        txs.detachFromSeries(recurrenceId)
        recurrences.delete(recurrenceId)
    }

    // --- Categories --------------------------------------------------------------------------

    suspend fun saveCategory(c: Category): Long =
        if (c.id == 0L) categories.insert(c) else c.also { categories.update(it) }.id

    /** Transactions in the deleted category become Uncategorized. */
    suspend fun deleteCategory(c: Category) {
        if (c.isSystem) return
        db.withTransaction {
            txs.reassignCategory(c.id, uncategorizedId())
            recurrences.reassignCategory(c.id, uncategorizedId())
            sellerRules.clearCategory(c.id)
            categories.delete(c)
        }
    }

    // --- Accounts ----------------------------------------------------------------------------

    suspend fun saveAccount(a: Account): Long =
        if (a.id == 0L) accounts.insert(a) else a.also { accounts.update(it) }.id

    suspend fun deleteAccount(a: Account, moveTo: Long) {
        db.withTransaction {
            txs.reassignAccount(a.id, moveTo)
            rules.reassignAccount(a.id, moveTo)
            recurrences.reassignAccount(a.id, moveTo)
            sharing.reassignSettlementAccount(a.id, moveTo)
            accounts.delete(a)
        }
    }

    // --- People and settle-ups -----------------------------------------------------------------

    fun observePeople(): Flow<List<Person>> = sharing.observePeople()

    /** Every person's history with you, keyed by person id. */
    fun observeLedger(): Flow<Map<Long, List<SharedEntry>>> =
        combine(sharing.observeSplitTransactions(), sharing.observeSplits(), sharing.observeSettlements(),
            sharing.observePeople()) { t, s, st, people ->
            Splits.ledger(t, s, st, people)
        }

    suspend fun savePerson(p: Person): Long =
        if (p.id == 0L) sharing.insertPerson(p) else p.also { sharing.updatePerson(it) }.id

    /** Deletes a person nothing refers to; returns false if they still have splits or settle-ups. */
    suspend fun deletePerson(id: Long): Boolean = db.withTransaction {
        if (sharing.isPersonUsed(id)) false else { sharing.deletePerson(id); true }
    }

    suspend fun saveSettlement(s: Settlement): Long =
        if (s.id == 0L) sharing.insertSettlement(s) else s.also { sharing.updateSettlement(it) }.id

    suspend fun deleteSettlement(id: Long) = sharing.deleteSettlement(id)

    // --- Import / export ---------------------------------------------------------------------

    suspend fun importCsv(content: String): ImportSummary {
        ensureSeeded()
        val parsed = Csv.parse(content)
        var newCats = 0
        var added = 0
        var skipped = 0
        db.withTransaction {
            val cats = categories.getAll().associateBy { it.name.lowercase() }.toMutableMap()
            val accs = accounts.getAll().associateBy { it.name.lowercase() }.toMutableMap()

            // Count existing rows so re-importing the same file adds nothing, while genuine
            // identical purchases on the same day inside one file are all kept.
            val existing = HashMap<String, Int>()
            val catNames = cats.values.associate { it.id to it.name.lowercase() }
            val accNames = accs.values.associate { it.id to it.name.lowercase() }
            for (t in txs.getAll()) {
                val k = dedupKey(t.timestamp.toLocalDate().toString(), catNames[t.categoryId], t.signedCents, t.note, accNames[t.accountId])
                existing.merge(k, 1, Int::plus)
            }

            val toInsert = mutableListOf<Transaction>()
            for (r in parsed.rows) {
                val k = dedupKey(r.date.toString(), r.category.lowercase(), r.signedCents, r.note, r.account.lowercase())
                val n = existing[k] ?: 0
                if (n > 0) { existing[k] = n - 1; skipped++; continue }

                val type = if (r.signedCents < 0) TxType.EXPENSE else TxType.INCOME
                val cat = cats.getOrPut(r.category.lowercase()) {
                    newCats++
                    val c = Category(name = r.category, type = type, color = Defaults.colorFor(cats.size),
                        icon = Defaults.suggestIcon(r.category))
                    c.copy(id = categories.insert(c))
                }
                val acc = accs.getOrPut(r.account.lowercase()) {
                    val a = Account(name = r.account, color = Defaults.colorFor(accs.size))
                    a.copy(id = accounts.insert(a))
                }
                toInsert += Transaction(
                    timestamp = r.date.toMillis() + 12L * 3600 * 1000, // midday: safe across DST shifts
                    amountCents = kotlin.math.abs(r.signedCents), type = type,
                    categoryId = cat.id, accountId = acc.id, note = r.note, source = TxSource.IMPORT,
                    merchant = r.seller.ifEmpty { null },
                )
                added++
            }
            txs.insertAll(toInsert)
        }
        return ImportSummary(added, skipped, newCats, parsed.errors)
    }

    private fun dedupKey(date: String, cat: String?, cents: Long, note: String, acc: String?) =
        "$date|$cat|$cents|${note.trim()}|$acc"

    suspend fun exportCsv(): String {
        val cats = categories.getAll().associateBy { it.id }
        val accs = accounts.getAll().associateBy { it.id }
        return Csv.write(txs.getAll().map {
            CsvRow(it.timestamp.toLocalDate(), cats[it.categoryId]?.name.orEmpty(), it.signedCents,
                it.note, accs[it.accountId]?.name.orEmpty(), it.merchant.orEmpty())
        })
    }

    suspend fun exportBackup(): String = Backup.toJson(
        BackupData(categories.getAll(), accounts.getAll(), txs.getAll(), sellerRules.getAll(), rules.getAll(), recurrences.getAll(),
            sharing.getPeople(), sharing.getSplits(), sharing.getSettlements())
    )

    /** Replaces all data with the backup's contents. */
    suspend fun restoreBackup(json: String): BackupData {
        val data = Backup.fromJson(json)
        val b = db.backup()
        db.withTransaction {
            b.clearSettlements(); b.clearSplits()
            b.clearTransactions(); b.clearSellerRules(); b.clearRecurrences(); b.clearRules(); b.clearCategories(); b.clearAccounts()
            b.clearPeople()
            b.insertCategories(data.categories)
            b.insertAccounts(data.accounts)
            b.insertPeople(data.people)
            b.insertTransactions(data.transactions)
            b.insertSplits(data.splits)
            b.insertSettlements(data.settlements)
            b.insertSellerRules(data.sellerRules)
            b.insertRecurrences(data.recurrences)
            b.insertRules(data.rules)
            notificationLog.unlinkAll()
        }
        uncategorizedIdCache = 0L
        seeded = false
        ensureSeeded()
        return data
    }
}
