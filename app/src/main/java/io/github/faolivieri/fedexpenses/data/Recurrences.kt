package io.github.faolivieri.fedexpenses.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.faolivieri.fedexpenses.util.toMillis
import java.time.LocalDate

enum class Frequency { WEEKLY, MONTHLY, YEARLY }

/**
 * A repeating transaction. Occurrence n falls on [startDay] + n periods, always counted from the
 * start so month ends don't drift (Jan 31 → Feb 28 → Mar 31).
 */
@Entity(tableName = "recurrences")
data class Recurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val frequency: Frequency,
    /** Epoch day of occurrence 0. */
    val startDay: Long,
    /** Index of the next occurrence that hasn't been created yet. */
    val nextIndex: Int,
    /** Epoch day after which nothing more is created, or null to repeat forever. */
    val endDay: Long?,
    // Template for the created transactions.
    val amountCents: Long,
    val type: TxType,
    val categoryId: Long,
    val accountId: Long,
    val note: String,
    val merchant: String?,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startDay)
    val end: LocalDate? get() = endDay?.let(LocalDate::ofEpochDay)

    fun occurrence(n: Int): LocalDate = Recurrences.occurrence(frequency, start, n)

    /** Date of the next transaction this series will create, or null if it has ended. */
    val nextDate: LocalDate?
        get() = occurrence(nextIndex).takeIf { d -> end?.let { d <= it } ?: true }

    /** The transaction for one occurrence, timestamped at the start of its day. */
    fun transactionOn(day: LocalDate) = Transaction(
        timestamp = day.toMillis(), amountCents = amountCents, type = type, categoryId = categoryId,
        accountId = accountId, note = note, merchant = merchant, source = TxSource.RECURRING, recurrenceId = id,
    )

    companion object {
        fun from(tx: Transaction, frequency: Frequency, start: LocalDate, nextIndex: Int, endDay: Long?) = Recurrence(
            frequency = frequency, startDay = start.toEpochDay(), nextIndex = nextIndex, endDay = endDay,
            amountCents = tx.amountCents, type = tx.type, categoryId = tx.categoryId, accountId = tx.accountId,
            note = tx.note, merchant = tx.merchant,
        )
    }
}

object Recurrences {
    fun occurrence(frequency: Frequency, start: LocalDate, n: Int): LocalDate = when (frequency) {
        Frequency.WEEKLY -> start.plusWeeks(n.toLong())
        Frequency.MONTHLY -> start.plusMonths(n.toLong())
        Frequency.YEARLY -> start.plusYears(n.toLong())
    }

    /** Occurrences (index to date) that are due by [today] and not created yet, oldest first. */
    fun due(r: Recurrence, today: LocalDate, limit: Int = 1000): List<Pair<Int, LocalDate>> {
        val out = mutableListOf<Pair<Int, LocalDate>>()
        var n = r.nextIndex
        while (out.size < limit) {
            val d = r.occurrence(n)
            if (d > today || (r.end != null && d > r.end!!)) break
            out += n to d
            n++
        }
        return out
    }

    /** First index ≥ 1 whose date is after [after], for a series starting on [start]. */
    fun firstIndexAfter(frequency: Frequency, start: LocalDate, after: LocalDate): Int {
        var n = 1
        while (occurrence(frequency, start, n) <= after && n < 100_000) n++
        return n
    }
}
