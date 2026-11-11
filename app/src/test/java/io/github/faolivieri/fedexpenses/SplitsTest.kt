package io.github.faolivieri.fedexpenses

import io.github.faolivieri.fedexpenses.data.Settlement
import io.github.faolivieri.fedexpenses.data.SharedEntry
import io.github.faolivieri.fedexpenses.data.SplitPart
import io.github.faolivieri.fedexpenses.data.Splits
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitsTest {
    private val ana = 1L
    private val marko = 2L

    private fun tx(id: Long, cents: Long, paidBy: Long? = null) = Transaction(
        id = id, timestamp = id * 1000, amountCents = cents, type = TxType.EXPENSE, categoryId = 1, accountId = 1,
        paidBy = paidBy,
    )

    private fun split(txId: Long, vararg parts: Pair<Long?, Int>) =
        parts.map { (p, bp) -> SplitPart(transactionId = txId, personId = p, basisPoints = bp) }

    @Test fun equalPartsAddUpToTheWhole() {
        assertEquals(listOf(5000, 5000), Splits.equal(2))
        assertEquals(listOf(3334, 3333, 3333), Splits.equal(3))
        assertEquals(Splits.WHOLE, Splits.equal(7).sum())
    }

    @Test fun amountsComeBackFromBasisPoints() {
        val ids = listOf<Long?>(null, ana, marko)
        fun roundTrip(amounts: List<Long>, payer: Int): List<Long> {
            val bp = Splits.fromAmounts(amounts, payer)!!
            assertEquals(Splits.WHOLE, bp.sum())
            val parts = Splits.parts(amounts.sum(), ids.zip(bp).map { (p, b) -> SplitPart(transactionId = 1, personId = p, basisPoints = b) }, ids[payer])
            return ids.map { parts.getValue(it) }
        }
        for (total in listOf(1L, 7L, 999L, 1000L, 4321L, 9999L, 10000L)) {
            for (a in 0..total step maxOf(1, total / 37)) for (b in 0..(total - a) step maxOf(1, total / 41)) {
                val amounts = listOf(a, b, total - a - b)
                for (payer in 0..2) assertEquals(amounts, roundTrip(amounts, payer))
            }
        }
        // Larger totals: every part within half a basis point of the total, the total exact.
        val big = listOf(4730L, 2L, 32_789L)
        val got = roundTrip(big, 0)
        assertEquals(big.sum(), got.sum())
        got.zip(big).drop(1).forEach { (g, w) -> assert(kotlin.math.abs(g - w) <= big.sum() / 20_000 + 1) }
        assertEquals(listOf(0, 10000, 0), Splits.fromAmounts(listOf(0, 5, 0), 0))
        assertEquals(null, Splits.fromAmounts(listOf(0, 0), 0))
    }

    @Test fun roundingRemainderGoesToThePayer() {
        val s = split(1, null to 3334, ana to 3333, marko to 3333)
        val paidByMe = Splits.parts(1000, s, null)
        assertEquals(1000L, paidByMe.values.sum())
        assertEquals(334L, paidByMe[null])

        val paidByAna = Splits.parts(1000, s, ana)
        assertEquals(1000L, paidByAna.values.sum())
        assertEquals(334L, paidByAna[ana])
        assertEquals(333L, paidByAna[null])
    }

    @Test fun equalSplitsDivideTheAmountItself() {
        val s = split(1, null to 3334, ana to 3333, marko to 3333)
        assertEquals(mapOf<Long?, Long>(null to 10000L, ana to 10000L, marko to 10000L), Splits.parts(30000, s, ana))
        // 12.50 three ways: the payer and the next participant get the two leftover cents.
        assertEquals(mapOf<Long?, Long>(ana to 417L, null to 417L, marko to 416L), Splits.parts(1250, s, ana))
    }

    @Test fun myShareIsTheWholeAmountUnlessSplit() {
        assertEquals(6000L, Splits.mine(tx(1, 6000), emptyList()))
        assertEquals(2000L, Splits.mine(tx(1, 6000), split(1, null to 3334, ana to 3333, marko to 3333)))
    }

    @Test fun owedDependsOnWhoPaid() {
        val s = split(1, null to 5000, ana to 5000)
        assertEquals(1500L, Splits.owed(tx(1, 3000), s, ana))
        assertEquals(-1500L, Splits.owed(tx(1, 3000, paidBy = ana), s, ana))
        // Ana paid a three-way split: between Marko and me nothing changes.
        val three = split(2, null to 3334, ana to 3333, marko to 3333)
        assertEquals(0L, Splits.owed(tx(2, 3000, paidBy = ana), three, marko))
    }

    @Test fun ledgerBalancesAddUpWithSettlements() {
        val txs = listOf(tx(1, 3000), tx(2, 1000, paidBy = ana))
        val splits = split(1, null to 5000, ana to 5000) + split(2, null to 5000, ana to 5000)
        val paid = Settlement(id = 1, personId = ana, timestamp = 5000, amountCents = 1000, fromMe = false, accountId = 1)

        val ledger = Splits.ledger(txs, splits, listOf(paid))
        val entries = ledger.getValue(ana)
        // Ana owes 15,00 for the first, I owe 5,00 for the second, she paid me back 10,00.
        assertEquals(0L, entries.sumOf { it.owedCents })
        assertEquals(listOf(5000L, 2000L, 1000L), entries.map { it.timestamp })
        assertEquals(500L, (entries[1] as SharedEntry.Expense).theirCents)
    }
}
