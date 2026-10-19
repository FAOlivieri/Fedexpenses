package io.github.faolivieri.fedexpenses

import io.github.faolivieri.fedexpenses.data.Person
import io.github.faolivieri.fedexpenses.data.Settlement
import io.github.faolivieri.fedexpenses.data.SharedEntry
import io.github.faolivieri.fedexpenses.data.SplitPart
import io.github.faolivieri.fedexpenses.data.Splits
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge cases around rounding, who paid, and settling up. */
class SplitsEdgeCasesTest {
    private val ana = 1L
    private val marko = 2L

    private fun tx(id: Long, cents: Long, paidBy: Long? = null) = Transaction(
        id = id, timestamp = id * 1000, amountCents = cents, type = TxType.EXPENSE, categoryId = 1, accountId = 1,
        paidBy = paidBy,
    )

    private fun split(txId: Long, vararg parts: Pair<Long?, Int>) =
        parts.map { (p, bp) -> SplitPart(transactionId = txId, personId = p, basisPoints = bp) }

    private fun balance(person: Long, txs: List<Transaction>, splits: List<SplitPart>, settlements: List<Settlement> = emptyList()) =
        Splits.ledger(txs, splits, settlements)[person].orEmpty().sumOf { it.owedCents }

    @Test fun threeWaysOfAnAmountThatDoesNotDivideEvenly() {
        // 10.00 three ways: 3.34 + 3.33 + 3.33, and the extra cent goes to whoever paid.
        val bp = Splits.equal(3)
        val s = split(1, null to bp[0], ana to bp[1], marko to bp[2])
        val parts = Splits.parts(1000, s, paidBy = null)
        assertEquals(1000L, parts.values.sum())
        assertEquals(setOf(333L, 334L), parts.values.toSet())
        assertEquals(333L, parts[ana])
        assertEquals(333L, parts[marko])
        assertEquals(334L, Splits.mine(tx(1, 1000), s))
    }

    @Test fun oddCentWithTwoPeopleNeverLosesMoney() {
        for (cents in listOf(1L, 3L, 99L, 101L, 999L, 123457L)) {
            val s = split(1, null to 5000, ana to 5000)
            for (payer in listOf(null, ana)) {
                val parts = Splits.parts(cents, s, payer)
                assertEquals("total $cents paid by $payer", cents, parts.values.sum())
            }
        }
    }

    @Test fun unevenPercentages() {
        // 70 / 30 of 45.00 that I paid: Ana owes 13.50, my share is 31.50.
        val t = tx(1, 4500)
        val s = split(1, null to 7000, ana to 3000)
        assertEquals(3150L, Splits.mine(t, s))
        assertEquals(1350L, Splits.owed(t, s, ana))
    }

    @Test fun someoneElsePaidForThree() {
        // Ana paid 30.00 for the three of us: I owe Ana my 10.00; Marko owes Ana, not me.
        val t = tx(1, 3000, paidBy = ana)
        val s = split(1, null to 3334, ana to 3333, marko to 3333)
        assertEquals(1000L, Splits.parts(3000, s, ana)[null])
        assertEquals(-1000L, Splits.owed(t, s, ana))
        assertEquals(0L, Splits.owed(t, s, marko))
        assertEquals(-1000L, balance(ana, listOf(t), s))
        assertEquals(0L, balance(marko, listOf(t), s))
    }

    @Test fun iPaidForOthersWithoutAShare() {
        // I paid 20.00 for Ana and Marko only: it isn't my expense, and each owes me 10.00.
        val t = tx(1, 2000)
        val s = split(1, ana to 5000, marko to 5000)
        assertEquals(0L, Splits.mine(t, s))
        assertEquals(1000L, Splits.owed(t, s, ana))
        assertEquals(1000L, Splits.owed(t, s, marko))
        assertEquals(2000L, Splits.parts(2000, s, null).values.sum())
    }

    @Test fun oweThenSettleUpBackToZero() {
        // Dinner Ana paid (I owe 25.00), groceries I paid (Ana owes 12.00): I owe 13.00 net; I pay it.
        val dinner = tx(1, 5000, paidBy = ana)
        val groceries = tx(2, 2400)
        val splits = split(1, null to 5000, ana to 5000) + split(2, null to 5000, ana to 5000)
        assertEquals(-1300L, balance(ana, listOf(dinner, groceries), splits))
        val payBack = Settlement(personId = ana, timestamp = 3000, amountCents = 1300, fromMe = true, accountId = 1)
        assertEquals(0L, balance(ana, listOf(dinner, groceries), splits, listOf(payBack)))
        // And the opposite direction: Ana paying me moves the balance the other way.
        val anaPays = Settlement(personId = ana, timestamp = 3000, amountCents = 1300, fromMe = false, accountId = 1)
        assertEquals(-2600L, balance(ana, listOf(dinner, groceries), splits, listOf(anaPays)))
    }

    @Test fun startingBalanceCountsAndIsTheOldestEntry() {
        // Carried over from another app: Ana owed me 40.00. Since then she paid a 30.00 dinner for both of us.
        val people = listOf(Person(id = ana, nickname = "Ana", startingBalanceCents = 4000))
        val dinner = tx(1, 3000, paidBy = ana)
        val s = split(1, null to 5000, ana to 5000)
        val entries = Splits.ledger(listOf(dinner), s, emptyList(), people)[ana]!!
        assertEquals(2500L, entries.sumOf { it.owedCents })          // 40.00 − 15.00
        assertTrue(entries.last() is SharedEntry.Starting)            // listed last, as the oldest
    }

    @Test fun personWithOnlyAStartingBalanceAppearsAndZeroAddsNothing() {
        val people = listOf(
            Person(id = ana, nickname = "Ana", startingBalanceCents = -1250), // I owe Ana 12.50
            Person(id = marko, nickname = "Marko"),
        )
        val ledger = Splits.ledger(emptyList(), emptyList(), emptyList(), people)
        assertEquals(-1250L, ledger[ana]!!.sumOf { it.owedCents })
        assertEquals(null, ledger[marko])
        // Settling exactly that brings it to zero.
        val pay = Settlement(personId = ana, timestamp = 1, amountCents = 1250, fromMe = true, accountId = 1)
        assertEquals(0L, Splits.ledger(emptyList(), emptyList(), listOf(pay), people)[ana]!!.sumOf { it.owedCents })
    }

    @Test fun ledgerIsNewestFirstAndOnlyIncludesPeopleInvolved() {
        val t1 = tx(1, 1000)
        val t2 = tx(5, 1000)
        val splits = split(1, null to 5000, ana to 5000) + split(5, null to 5000, ana to 5000)
        val pay = Settlement(personId = ana, timestamp = 3000, amountCents = 500, fromMe = false, accountId = 1)
        val ledger = Splits.ledger(listOf(t1, t2), splits, listOf(pay))
        assertEquals(listOf(5000L, 3000L, 1000L), ledger[ana]!!.map { it.timestamp })
        assertEquals(null, ledger[marko])
    }
}
