package io.github.faolivieri.fedexpenses

import io.github.faolivieri.fedexpenses.data.NotificationRule
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.notify.ParseResult
import io.github.faolivieri.fedexpenses.notify.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleDirectionsTest {
    private val bank = NotificationRule(
        name = "Bank", packageName = "si.bank.app", accountId = 1,
        expensePattern = "Card payment of (?<amount>[\\d.,]+) EUR at (?<merchant>.+)",
        incomePattern = "Received (?<amount>[\\d.,]+) EUR from (?<merchant>.+)",
        incomeEnabled = true,
    )

    /** The same first-match logic the notification processor uses. */
    private fun classify(rule: NotificationRule, title: String, text: String): Pair<TxType, Long>? =
        rule.activePatterns.firstNotNullOfOrNull { (type, pattern) ->
            (RuleEngine.parse(pattern, title, text) as? ParseResult.Match)?.let { type to it.amountCents }
        }

    @Test fun expenseAndIncomeAreRecognisedSeparately() {
        assertEquals(TxType.EXPENSE to 2340L, classify(bank, "Bank", "Card payment of 23,40 EUR at SPAR"))
        assertEquals(TxType.INCOME to 150000L, classify(bank, "Bank", "Received 1.500,00 EUR from ACME d.o.o."))
        assertEquals(null, classify(bank, "Bank", "Your statement is ready"))
    }

    @Test fun incomeIsTriedBeforeACatchAllExpensePattern() {
        // Google Wallet's pattern matches any "title + amount", including incoming money.
        val catchAll = bank.copy(expensePattern = io.github.faolivieri.fedexpenses.data.Defaults.GOOGLE_WALLET_PATTERN)
        assertEquals(TxType.INCOME to 5000L, classify(catchAll, "Bank", "Received 50.00 EUR from Ana"))
        assertEquals(TxType.EXPENSE to 420L, classify(catchAll, "Bakery Kruh", "€4.20 with Visa"))
    }

    @Test fun disabledDirectionsAreSkipped() {
        val incomeOff = bank.copy(incomeEnabled = false)
        assertEquals(listOf(TxType.EXPENSE), incomeOff.activePatterns.map { it.first })
        assertEquals(null, classify(incomeOff, "Bank", "Received 1.500,00 EUR from ACME d.o.o."))
        // A blank pattern counts as off even when its switch is on.
        assertEquals(listOf(TxType.INCOME), bank.copy(expensePattern = "").activePatterns.map { it.first })
    }
}
