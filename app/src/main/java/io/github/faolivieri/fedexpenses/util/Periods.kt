package io.github.faolivieri.fedexpenses.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class PeriodType { MONTH, YEAR }

/** A calendar month or year. [month] is 1..12 and ignored for years. */
data class Period(val type: PeriodType, val year: Int, val month: Int) {
    val start: LocalDate
        get() = if (type == PeriodType.MONTH) LocalDate.of(year, month, 1) else LocalDate.of(year, 1, 1)

    val endExclusive: LocalDate
        get() = if (type == PeriodType.MONTH) start.plusMonths(1) else start.plusYears(1)

    val startMillis: Long get() = start.toMillis()
    val endMillis: Long get() = endExclusive.toMillis()

    fun shift(by: Int): Period = if (type == PeriodType.MONTH) {
        val ym = YearMonth.of(year, month).plusMonths(by.toLong())
        copy(year = ym.year, month = ym.monthValue)
    } else {
        copy(year = year + by)
    }

    fun withType(t: PeriodType): Period = copy(type = t)

    val label: String
        get() = if (type == PeriodType.MONTH) {
            YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        } else year.toString()

    /** "Sep 2026" / "2026", for narrow screens. */
    val shortLabel: String
        get() = if (type == PeriodType.MONTH) {
            YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        } else year.toString()

    val isCurrent: Boolean
        get() {
            val now = LocalDate.now()
            return year == now.year && (type == PeriodType.YEAR || month == now.monthValue)
        }

    companion object {
        fun current(type: PeriodType = PeriodType.MONTH): Period {
            val now = LocalDate.now()
            return Period(type, now.year, now.monthValue)
        }
    }
}

fun LocalDate.toMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

object Dates {
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val dayHeaderYear = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val short = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun dayHeader(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> (if (d.year == today.year) d.format(dayHeader) else d.format(dayHeaderYear))
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }

    fun short(d: LocalDate): String = d.format(short)

    fun monthShort(ym: YearMonth): String =
        ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + (ym.year % 100).toString().padStart(2, '0')
}
