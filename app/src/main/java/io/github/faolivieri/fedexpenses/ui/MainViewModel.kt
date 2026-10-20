package io.github.faolivieri.fedexpenses.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.faolivieri.fedexpenses.container
import io.github.faolivieri.fedexpenses.data.Account
import io.github.faolivieri.fedexpenses.data.Category
import io.github.faolivieri.fedexpenses.data.Frequency
import io.github.faolivieri.fedexpenses.data.ImportChoice
import io.github.faolivieri.fedexpenses.data.Incoming
import io.github.faolivieri.fedexpenses.data.SharePayload
import io.github.faolivieri.fedexpenses.data.Person
import io.github.faolivieri.fedexpenses.data.Recurrence
import io.github.faolivieri.fedexpenses.data.SellerRule
import io.github.faolivieri.fedexpenses.data.Settlement
import io.github.faolivieri.fedexpenses.data.SharedEntry
import io.github.faolivieri.fedexpenses.data.SplitPart
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxDetails
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.util.Dates
import io.github.faolivieri.fedexpenses.util.Period
import io.github.faolivieri.fedexpenses.util.toLocalDate
import io.github.faolivieri.fedexpenses.util.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class TxFilter(
    val query: String = "",
    val type: TxType? = null,
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
) {
    val isActive get() = query.isNotBlank() || type != null || categoryIds.isNotEmpty() || accountIds.isNotEmpty()

    fun matches(d: TxDetails): Boolean {
        if (type != null && d.tx.type != type) return false
        if (categoryIds.isNotEmpty() && d.category.id !in categoryIds) return false
        if (accountIds.isNotEmpty() && d.account.id !in accountIds) return false
        if (query.isNotBlank()) {
            val q = query.trim()
            return d.tx.note.contains(q, true) || d.category.name.contains(q, true) ||
                (d.tx.merchant?.contains(q, true) == true) || d.account.name.contains(q, true)
        }
        return true
    }
}

data class CategoryTotal(val category: Category, val cents: Long, val count: Int)

data class Overview(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val expensesByCategory: List<CategoryTotal> = emptyList(),
    val incomeByCategory: List<CategoryTotal> = emptyList(),
) {
    val balanceCents get() = incomeCents - expenseCents
}

enum class Granularity { MONTH, YEAR }

sealed interface SeriesKey {
    data object AllExpenses : SeriesKey
    data object AllIncomes : SeriesKey
    data class Cat(val id: Long) : SeriesKey
}

data class ReportConfig(
    val from: YearMonth = YearMonth.now().minusMonths(11),
    val to: YearMonth = YearMonth.now(),
    val granularity: Granularity = Granularity.MONTH,
    val series: List<SeriesKey> = listOf(SeriesKey.AllExpenses, SeriesKey.AllIncomes),
)

data class ReportSeries(val key: SeriesKey, val name: String, val color: Int?, val type: TxType, val values: List<Long>) {
    val total get() = values.sum()
    val average get() = if (values.isEmpty()) 0 else total / values.size
}

data class ReportData(val labels: List<String>, val series: List<ReportSeries>)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.container.repo
    private val settings = app.container.settings

    init {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    val period = MutableStateFlow(Period.current())
    val filter = MutableStateFlow(TxFilter())
    val report = MutableStateFlow(ReportConfig())

    val categories: StateFlow<List<Category>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<Account>> =
        repo.observeAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categoryUsage: StateFlow<Map<Long, Int>> =
        repo.observeCategoryUsage().map { l -> l.associate { it.id to it.n } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val accountBalances: StateFlow<Map<Long, Long>> =
        repo.observeAccountBalances().map { l -> l.associate { it.id to it.total } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val periodTx: StateFlow<List<TxDetails>?> =
        period.flatMapLatest { repo.observeDetails(it.startMillis, it.endMillis) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val overview: StateFlow<Overview?> = periodTx.map { list -> list?.let(::summarize) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val filteredTx: StateFlow<List<TxDetails>?> = combine(periodTx, filter) { list, f ->
        list?.filter(f::matches)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sellerRules: StateFlow<List<SellerRule>> =
        repo.observeSellerRules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val detectedPayments: StateFlow<List<Transaction>> =
        repo.observeDetectedPayments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val earliestMonth: StateFlow<YearMonth?> = repo.observeEarliest()
        .map { it?.toLocalDate()?.let(YearMonth::from) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val reportData: StateFlow<ReportData?> = report.flatMapLatest { cfg ->
        repo.observeDetails(cfg.from.atDay(1).toMillis(), cfg.to.plusMonths(1).atDay(1).toMillis())
            .combine(categories) { list, cats -> buildReport(cfg, list, cats) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun summarize(list: List<TxDetails>): Overview {
        fun byCategory(type: TxType) = list.filter { it.tx.type == type }
            .groupBy { it.category }
            .map { (c, l) -> CategoryTotal(c, l.sumOf { it.myCents }, l.size) }
            .sortedByDescending { it.cents }
        val exp = byCategory(TxType.EXPENSE)
        val inc = byCategory(TxType.INCOME)
        return Overview(inc.sumOf { it.cents }, exp.sumOf { it.cents }, exp, inc)
    }

    private fun buildReport(cfg: ReportConfig, list: List<TxDetails>, cats: List<Category>): ReportData {
        val months = generateSequence(cfg.from) { it.plusMonths(1) }.takeWhile { it <= cfg.to }.toList()
        val bucketKeys: List<Int> = if (cfg.granularity == Granularity.MONTH) months.map { it.year * 12 + it.monthValue }
        else (cfg.from.year..cfg.to.year).toList()
        val labels = if (cfg.granularity == Granularity.MONTH) months.map(Dates::monthShort) else bucketKeys.map { it.toString() }
        val index = bucketKeys.withIndex().associate { (i, k) -> k to i }
        fun bucketOf(t: Transaction): Int? {
            val d = t.timestamp.toLocalDate()
            return index[if (cfg.granularity == Granularity.MONTH) d.year * 12 + d.monthValue else d.year]
        }
        val catMap = cats.associateBy { it.id }
        val series = cfg.series.mapNotNull { key ->
            val values = LongArray(bucketKeys.size)
            val (name, color, type) = when (key) {
                SeriesKey.AllExpenses -> Triple("All expenses", null, TxType.EXPENSE)
                SeriesKey.AllIncomes -> Triple("All incomes", null, TxType.INCOME)
                is SeriesKey.Cat -> catMap[key.id]?.let { Triple(it.name, it.color, it.type) } ?: return@mapNotNull null
            }
            for (d in list) {
                val inSeries = when (key) {
                    SeriesKey.AllExpenses -> d.tx.type == TxType.EXPENSE
                    SeriesKey.AllIncomes -> d.tx.type == TxType.INCOME
                    is SeriesKey.Cat -> d.tx.categoryId == key.id
                }
                if (!inSeries) continue
                val b = bucketOf(d.tx) ?: continue
                // A refund in an expense category reduces that category's spending.
                values[b] += if (d.tx.type == type) d.myCents else -d.myCents
            }
            ReportSeries(key, name, color, type, values.toList())
        }
        return ReportData(labels, series)
    }

    // --- Actions -----------------------------------------------------------------------------

    fun saveTransaction(tx: Transaction, splits: List<SplitPart>? = null) =
        viewModelScope.launch { repo.saveTransaction(tx, splits) }
    suspend fun loadSplits(txId: Long): List<SplitPart> = repo.splitsFor(txId)
    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }
    suspend fun loadTransaction(id: Long): Transaction? = repo.getTransaction(id)

    suspend fun saveCategory(c: Category): Long = repo.saveCategory(c)
    fun deleteCategory(c: Category) = viewModelScope.launch { repo.deleteCategory(c) }

    val recurrences: StateFlow<List<Recurrence>> =
        repo.observeRecurrences().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getRecurrence(id: Long): Recurrence? = repo.getRecurrence(id)
    suspend fun latestIdInSeries(recurrenceId: Long): Long? = repo.latestIdInSeries(recurrenceId)
    // Series changes run in the ViewModel scope so leaving the editor can't cancel them.
    fun startRecurring(tx: Transaction, f: Frequency, endDay: Long?) =
        viewModelScope.launch { repo.startRecurring(tx, f, endDay) }
    fun updateSeries(tx: Transaction, originalDay: LocalDate, f: Frequency, endDay: Long?) =
        viewModelScope.launch { repo.updateSeries(tx, originalDay, f, endDay) }
    fun stopRecurring(recurrenceId: Long) = viewModelScope.launch { repo.stopRecurring(recurrenceId) }

    suspend fun getSellerRule(id: Long): SellerRule? = repo.sellerRules.get(id)
    suspend fun saveSellerRule(r: SellerRule): Long = repo.saveSellerRule(r)
    fun deleteSellerRule(id: Long) = viewModelScope.launch { repo.deleteSellerRule(id) }
    fun reorderSellerRules(ids: List<Long>) = viewModelScope.launch { repo.reorderSellerRules(ids) }
    suspend fun setCategory(txIds: List<Long>, categoryId: Long, note: String? = null) =
        repo.setCategory(txIds, categoryId, note)

    suspend fun saveAccount(a: Account): Long = repo.saveAccount(a)
    fun deleteAccount(a: Account, moveTo: Long) = viewModelScope.launch { repo.deleteAccount(a, moveTo) }

    val people: StateFlow<List<Person>> =
        repo.observePeople().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Each person's history with you, newest first, keyed by person id. */
    val ledger: StateFlow<Map<Long, List<SharedEntry>>> =
        repo.observeLedger().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    suspend fun savePerson(p: Person): Long = repo.savePerson(p)
    suspend fun deletePerson(id: Long): Boolean = repo.deletePerson(id)
    fun saveSettlement(s: Settlement) = viewModelScope.launch { repo.saveSettlement(s) }

    // --- Sharing between phones ----------------------------------------------------------------

    fun myNickname(): String = settings.myNickname
    fun setMyNickname(name: String) { settings.myNickname = name }

    /** A received share waiting for review. */
    val incomingShare = app.container.incomingShare

    suspend fun saveTransactionNow(tx: Transaction, splits: List<SplitPart>?): Long = repo.saveTransaction(tx, splits)

    /** Sends the expenses among [txIds] that are split with [personId], as a file. */
    fun share(context: Context, personId: Long, txIds: List<Long>) = viewModelScope.launch {
        val p = repo.buildShare(personId, txIds)
        val name = people.value.firstOrNull { it.id == personId }?.nickname.orEmpty()
        if (p.items.isEmpty()) Toast.makeText(context, "None of these are split with $name", Toast.LENGTH_LONG).show()
        else ShareActions.sendFile(context, p, name)
    }

    fun applySplit(txIds: List<Long>, personIds: List<Long>, paidBy: Long?) =
        viewModelScope.launch { repo.applySplit(txIds, personIds, paidBy) }

    suspend fun previewShare(p: SharePayload): List<Incoming> = repo.previewShare(p)
    suspend fun senderOf(p: SharePayload): Person? = repo.senderOf(p)
    suspend fun importShare(p: SharePayload, incoming: List<Incoming>, choices: List<ImportChoice>, senderId: Long?): Int =
        repo.importShare(p, incoming, choices, senderId)
    fun deleteSettlement(id: Long) = viewModelScope.launch { repo.deleteSettlement(id) }
}
