package io.github.faolivieri.fedexpenses

import io.github.faolivieri.fedexpenses.data.Frequency
import io.github.faolivieri.fedexpenses.data.Recurrence
import io.github.faolivieri.fedexpenses.data.Recurrences
import io.github.faolivieri.fedexpenses.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun series(f: Frequency, start: LocalDate, nextIndex: Int = 1, end: LocalDate? = null) = Recurrence(
        id = 7, frequency = f, startDay = start.toEpochDay(), nextIndex = nextIndex, endDay = end?.toEpochDay(),
        amountCents = 25000, type = TxType.EXPENSE, categoryId = 3, accountId = 1, note = "Rent", merchant = null,
    )

    @Test fun monthlyKeepsTheDayAfterShortMonths() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Recurrences.occurrence(Frequency.MONTHLY, start, 1))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrences.occurrence(Frequency.MONTHLY, start, 2))
        assertEquals(LocalDate.of(2026, 4, 30), Recurrences.occurrence(Frequency.MONTHLY, start, 3))
    }

    @Test fun yearlyFromLeapDay() {
        val start = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2029, 2, 28), Recurrences.occurrence(Frequency.YEARLY, start, 1))
        assertEquals(LocalDate.of(2032, 2, 29), Recurrences.occurrence(Frequency.YEARLY, start, 4))
    }

    @Test fun dueCatchesUpEverythingMissedAndIncludesToday() {
        val r = series(Frequency.WEEKLY, LocalDate.of(2026, 9, 1))
        val due = Recurrences.due(r, today = LocalDate.of(2026, 9, 22))
        assertEquals(listOf(1, 2, 3), due.map { it.first })
        assertEquals(LocalDate.of(2026, 9, 22), due.last().second)
    }

    @Test fun nothingDueBeforeTheDayOrAfterTheEnd() {
        val r = series(Frequency.MONTHLY, LocalDate.of(2026, 9, 15), end = LocalDate.of(2026, 11, 14))
        assertTrue(Recurrences.due(r, LocalDate.of(2026, 10, 14)).isEmpty())
        assertEquals(1, Recurrences.due(r, LocalDate.of(2026, 10, 15)).size)
        // Nov 15 is past the end date, so only October is ever created.
        assertEquals(1, Recurrences.due(r, LocalDate.of(2027, 6, 1)).size)
        assertNull(r.copy(nextIndex = 2).nextDate)
    }

    @Test fun futureFirstPaymentIsOccurrenceZero() {
        val r = series(Frequency.MONTHLY, LocalDate.of(2026, 11, 1), nextIndex = 0)
        assertEquals(LocalDate.of(2026, 11, 1), r.nextDate)
        assertTrue(Recurrences.due(r, LocalDate.of(2026, 10, 31)).isEmpty())
        assertEquals(listOf(0), Recurrences.due(r, LocalDate.of(2026, 11, 1)).map { it.first })
    }

    @Test fun restartSkipsDatesAlreadyCreated() {
        val start = LocalDate.of(2026, 7, 10)
        assertEquals(4, Recurrences.firstIndexAfter(Frequency.MONTHLY, start, after = LocalDate.of(2026, 10, 10)))
        assertEquals(1, Recurrences.firstIndexAfter(Frequency.MONTHLY, start, after = start))
    }

    @Test fun createdTransactionsStartAtMidnightAndBelongToTheSeries() {
        val day = LocalDate.of(2026, 11, 1)
        val tx = series(Frequency.MONTHLY, day).transactionOn(day)
        assertEquals(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), tx.timestamp)
        assertEquals(7L, tx.recurrenceId)
        assertEquals("Rent", tx.note)
    }
}
