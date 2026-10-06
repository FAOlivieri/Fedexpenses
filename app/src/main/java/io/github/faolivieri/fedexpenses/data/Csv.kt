package io.github.faolivieri.fedexpenses.data

import io.github.faolivieri.fedexpenses.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One row of the "Date, Category, Amount, Note, Account[, Seller]" export format. */
data class CsvRow(
    val date: LocalDate,
    val category: String,
    /** Signed: negative is an expense. */
    val signedCents: Long,
    val note: String,
    val account: String,
    val seller: String = "",
)

data class CsvParseResult(val rows: List<CsvRow>, val errors: List<String>)

object Csv {
    const val HEADER = "Date, Category, Amount, Note, Account, Seller"
    private val usDate = DateTimeFormatter.ofPattern("M/d/yyyy")

    fun parse(content: String): CsvParseResult {
        val rows = mutableListOf<CsvRow>()
        val errors = mutableListOf<String>()
        val lines = splitRecords(content.removePrefix("﻿"))
        // Files from the old app have 5 columns; ours add Seller as a 6th.
        val header = lines.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
        val columns = if ("seller" in header) 6 else 5
        lines.forEachIndexed { i, fields ->
            if (i == 0 && fields.firstOrNull()?.trim()?.equals("Date", ignoreCase = true) == true) return@forEachIndexed
            if (fields.all { it.isBlank() }) return@forEachIndexed
            val f = if (fields.size > columns) {
                // Unquoted commas inside the note: everything between amount and account is the note.
                val tail = columns - 4 // account, plus seller when present
                listOf(fields[0], fields[1], fields[2], fields.subList(3, fields.size - tail).joinToString(",")) +
                    fields.takeLast(tail)
            } else fields
            if (f.size < 3) {
                errors += "Line ${i + 1}: expected $columns columns"
                return@forEachIndexed
            }
            val date = parseDate(f[0])
            val amount = parseSignedCents(f[2])
            if (date == null || amount == null) {
                errors += "Line ${i + 1}: can't read date or amount"
                return@forEachIndexed
            }
            rows += CsvRow(
                date = date,
                category = f[1].trim().ifEmpty { Defaults.UNCATEGORIZED_NAME },
                signedCents = amount,
                note = f.getOrElse(3) { "" }.trim(),
                account = f.getOrElse(4) { "" }.trim().ifEmpty { "Personal" },
                seller = f.getOrElse(5) { "" }.trim(),
            )
        }
        return CsvParseResult(rows, errors)
    }

    fun write(rows: List<CsvRow>): String = buildString {
        append(HEADER).append('\n')
        for (r in rows) {
            append(r.date.format(usDate)).append(", ")
            append(quote(r.category)).append(", ")
            append(if (r.signedCents < 0) "-" else "").append(Money.editable(kotlin.math.abs(r.signedCents))).append(", ")
            append(quote(r.note)).append(", ")
            append(quote(r.account)).append(", ")
            append(quote(r.seller)).append('\n')
        }
    }

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun parseDate(s: String): LocalDate? {
        val t = s.trim()
        return runCatching { LocalDate.parse(t, usDate) }.getOrNull()
            ?: runCatching { LocalDate.parse(t) }.getOrNull()
    }

    private fun parseSignedCents(s: String): Long? {
        val t = s.trim()
        val negative = t.startsWith("-") || t.startsWith("−")
        val cents = Money.parseCents(t) ?: return null
        return if (negative) -cents else cents
    }

    /** Minimal RFC 4180 reader that also tolerates the ", " separator used by the old app. */
    private fun splitRecords(content: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                inQuotes && c == '"' && content.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                c == '"' && (inQuotes || sb.isBlank()) -> { if (!inQuotes) sb.clear(); inQuotes = !inQuotes }
                !inQuotes && c == ',' -> { fields += sb.toString(); sb.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && content.getOrNull(i + 1) == '\n') i++
                    fields += sb.toString(); sb.clear()
                    records += fields; fields = mutableListOf()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || fields.isNotEmpty()) {
            fields += sb.toString()
            records += fields
        }
        return records
    }
}
