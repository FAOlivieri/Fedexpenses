package io.github.faolivieri.fedexpenses.data

/** One line of the history with a person: a split expense or a settle-up. */
sealed interface SharedEntry {
    val timestamp: Long
    /** What the person owes you because of this entry; negative when you owe them. */
    val owedCents: Long

    data class Expense(val tx: Transaction, val theirCents: Long, override val owedCents: Long) : SharedEntry {
        override val timestamp get() = tx.timestamp
    }

    data class Payment(val settlement: Settlement) : SharedEntry {
        override val timestamp get() = settlement.timestamp
        override val owedCents get() = Splits.owed(settlement)
    }

    /** The balance carried over when you started tracking; always the oldest entry. */
    data class Starting(override val owedCents: Long) : SharedEntry {
        override val timestamp get() = Long.MIN_VALUE
    }
}

object Splits {
    const val WHOLE = 10_000

    /** Equal parts in basis points for [n] participants; the first ones get the rounding remainder. */
    fun equal(n: Int): List<Int> = List(n) { i -> WHOLE / n + if (i < WHOLE % n) 1 else 0 }

    /**
     * Each participant's part of [total] cents, keyed by person id (null is you). The parts add up to [total].
     * Uneven parts are rounded to the nearest cent and the rounding difference goes to [paidBy], or to the
     * first participant if [paidBy] has no part.
     */
    fun parts(total: Long, splits: List<SplitPart>, paidBy: Long?): Map<Long?, Long> {
        if (splits.isEmpty()) return emptyMap()
        val key = if (splits.any { it.personId == paidBy }) paidBy else splits.first().personId
        // An equal split divides the amount itself; leftover cents go one each, starting with the payer.
        if (splits.map { it.basisPoints }.sorted() == equal(splits.size).sorted()) {
            val base = total / splits.size
            val extra = total % splits.size
            return splits.map { it.personId }.sortedBy { it != key }
                .withIndex().associate { (i, id) -> id to base + if (i < extra) 1 else 0 }
        }
        val parts = splits.associate { it.personId to (total * it.basisPoints + WHOLE / 2) / WHOLE }.toMutableMap()
        parts[key] = parts.getValue(key) + total - parts.values.sum()
        return parts
    }

    /**
     * Basis points for parts given in cents, in the same order, adding up to [WHOLE]; null if they add up to 0.
     * [parts] turns them back into the same amounts for totals up to 100.00; on larger totals each part can be
     * off by half a basis point of the total, and the participant at [keyIndex] (the payer) takes the difference.
     */
    fun fromAmounts(cents: List<Long>, keyIndex: Int): List<Int>? {
        val total = cents.sum()
        if (total <= 0) return null
        val bp = cents.map { ((it * WHOLE * 2 + total) / (2 * total)).toInt() }.toMutableList()
        bp[keyIndex] = 0
        bp[keyIndex] = WHOLE - bp.sum()
        while (bp[keyIndex] < 0) {
            bp[bp.indices.maxBy { bp[it] }]--
            bp[keyIndex]++
        }
        return bp
    }

    fun mine(tx: Transaction, splits: List<SplitPart>): Long =
        if (splits.isEmpty()) tx.amountCents else parts(tx.amountCents, splits, tx.paidBy)[null] ?: 0

    /** What [personId] owes you because of [tx]; negative when you owe them. */
    fun owed(tx: Transaction, splits: List<SplitPart>, personId: Long): Long {
        val parts = parts(tx.amountCents, splits, tx.paidBy)
        return when (tx.paidBy) {
            null -> parts[personId] ?: 0
            personId -> -(parts[null] ?: 0)
            else -> 0
        }
    }

    fun owed(s: Settlement): Long = if (s.fromMe) s.amountCents else -s.amountCents

    /**
     * Each person's history, newest first, ending with their starting balance (from [people]) if any.
     * Only people with at least one entry appear.
     */
    fun ledger(
        txs: List<Transaction>, splits: List<SplitPart>, settlements: List<Settlement>,
        people: List<Person> = emptyList(),
    ): Map<Long, List<SharedEntry>> {
        val byTx = splits.groupBy { it.transactionId }
        val out = HashMap<Long, MutableList<SharedEntry>>()
        for (tx in txs) {
            val s = byTx[tx.id] ?: continue
            val parts = parts(tx.amountCents, s, tx.paidBy)
            val people = (s.mapNotNull { it.personId } + listOfNotNull(tx.paidBy)).distinct()
            for (p in people) {
                out.getOrPut(p) { mutableListOf() } += SharedEntry.Expense(tx, parts[p] ?: 0, owed(tx, s, p))
            }
        }
        for (st in settlements) out.getOrPut(st.personId) { mutableListOf() } += SharedEntry.Payment(st)
        for (p in people) {
            if (p.startingBalanceCents != 0L) out.getOrPut(p.id) { mutableListOf() } += SharedEntry.Starting(p.startingBalanceCents)
        }
        return out.mapValues { (_, l) -> l.sortedByDescending { it.timestamp } }
    }
}
