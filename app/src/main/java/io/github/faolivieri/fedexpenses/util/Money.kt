package io.github.faolivieri.fedexpenses.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

object Money {
    @Volatile
    var symbol: String = "€"

    private val formatter = ThreadLocal.withInitial {
        DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance())
    }

    /** "12.50 €" (no sign). */
    fun format(cents: Long): String = "${plain(cents)} $symbol"

    /** "-12.50 €" / "+12.50 €". */
    fun formatSigned(cents: Long): String =
        (if (cents < 0) "−" else "+") + format(kotlin.math.abs(cents))

    fun plain(cents: Long): String =
        formatter.get()!!.format(BigDecimal.valueOf(kotlin.math.abs(cents), 2))

    /** Compact axis label: 1.2k, 950. */
    fun compact(cents: Long): String {
        val v = cents / 100.0
        return when {
            kotlin.math.abs(v) >= 10_000 -> "%.0fk".format(v / 1000)
            kotlin.math.abs(v) >= 1_000 -> "%.1fk".format(v / 1000)
            else -> "%.0f".format(v)
        }
    }

    /** Value for an editable text field: "12.50". */
    fun editable(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    /**
     * Parses amounts as written in notifications or typed by hand, tolerating currency
     * symbols and either decimal convention: "€1.234,56", "1,234.56", "12,5", "$3".
     * Returns positive cents, or null if there is no number.
     */
    fun parseCents(raw: String): Long? {
        var s = raw.filter { it.isDigit() || it == '.' || it == ',' }.trim('.', ',')
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimal = if (lastDot > lastComma) '.' else ','
                val thousands = if (decimal == '.') ',' else '.'
                s.replace(thousands.toString(), "").replace(decimal, '.')
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val count = s.count { it == sep }
                val decimals = s.length - s.lastIndexOf(sep) - 1
                if (count > 1 || decimals == 3) s.replace(sep.toString(), "")
                else s.replace(sep, '.')
            }
            else -> s
        }
        return try {
            BigDecimal(s).setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
