package io.github.faolivieri.fedexpenses

import io.github.faolivieri.fedexpenses.data.Csv
import io.github.faolivieri.fedexpenses.data.CsvRow
import io.github.faolivieri.fedexpenses.data.Defaults
import io.github.faolivieri.fedexpenses.data.MatchType
import io.github.faolivieri.fedexpenses.data.SellerMatcher
import io.github.faolivieri.fedexpenses.data.SellerRule
import io.github.faolivieri.fedexpenses.data.normalizeMerchant
import io.github.faolivieri.fedexpenses.notify.ParseResult
import io.github.faolivieri.fedexpenses.notify.RuleEngine
import io.github.faolivieri.fedexpenses.util.Money
import io.github.faolivieri.fedexpenses.util.Period
import io.github.faolivieri.fedexpenses.util.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MoneyTest {
    @Test fun parsesBothDecimalConventions() {
        assertEquals(1250L, Money.parseCents("12.50"))
        assertEquals(1250L, Money.parseCents("12,50"))
        assertEquals(1250L, Money.parseCents("€12,5"))
        assertEquals(123456L, Money.parseCents("1.234,56 €"))
        assertEquals(123456L, Money.parseCents("$1,234.56"))
        assertEquals(123400L, Money.parseCents("1,234"))
        assertEquals(300L, Money.parseCents("3"))
        assertEquals(350L, Money.parseCents("€3.50."))
        assertEquals(100000000L, Money.parseCents("1.000.000"))
        assertNull(Money.parseCents("€"))
        assertNull(Money.parseCents(""))
    }
}

class RuleEngineTest {
    private val wallet = Defaults.GOOGLE_WALLET_PATTERN

    private fun match(title: String, text: String) = RuleEngine.parse(wallet, title, text) as ParseResult.Match

    @Test fun walletSymbolBefore() {
        val m = match("SPAR Ljubljana", "€12.50 with Visa •• 1234")
        assertEquals(1250L, m.amountCents)
        assertEquals("SPAR Ljubljana", m.merchant)
    }

    @Test fun walletSymbolAfterWithComma() {
        assertEquals(354L, match("Mercator", "3,54 € z Mastercard ••5678").amountCents)
    }

    @Test fun walletIgnoresCardDigitsBeforeAmount() {
        assertEquals(799L, match("Lidl", "Visa •• 1234 · EUR 7.99").amountCents)
    }

    @Test fun walletAmountAtMerchantInTitle() {
        val m = match("€4.50 at Costa Coffee", "Visa •• 1234")
        assertEquals(450L, m.amountCents)
        assertEquals("Costa Coffee", m.merchant)
    }

    @Test fun walletAmountAtMerchantInTextWinsOverGenericTitle() {
        val m = match("Payment successful", "You paid 12,00 € to Hofer")
        assertEquals(1200L, m.amountCents)
        assertEquals("Hofer", m.merchant)
    }

    @Test fun walletNoCurrencyIsNoMatch() {
        assertEquals(ParseResult.NoMatch, RuleEngine.parse(wallet, "Google Wallet", "Your card 1234 is ready to use"))
    }

    @Test fun customBankRuleWithoutMerchantGroupUsesTitle() {
        val r = RuleEngine.parse("Spent (?<amount>[\\d.,]+) EUR", "NLB Klik", "Spent 45,00 EUR at card terminal")
        assertEquals(ParseResult.Match(4500, "NLB Klik"), r)
    }

    @Test fun customBankRuleWithMerchantGroup() {
        val r = RuleEngine.parse("(?s)Payment of (?<amount>[\\d.,]+) EUR at (?<merchant>.+?)\\. Balance", "Revolut",
            "Payment of 9.90 EUR at Netflix.com. Balance: 100 EUR") as ParseResult.Match
        assertEquals(990L, r.amountCents)
        assertEquals("Netflix.com", r.merchant)
    }

    @Test fun invalidPatternIsReported() {
        assertTrue(RuleEngine.parse("(?<amount>[", "a", "b") is ParseResult.InvalidPattern)
    }

    @Test fun merchantKeysIgnoreCaseAndSpacing() {
        assertEquals(normalizeMerchant("  SPAR   Ljubljana "), normalizeMerchant("spar ljubljana"))
    }
}

class CsvTest {
    @Test fun parsesTheSampleFile() {
        val r = Csv.parse(File("../sample-transactions.csv").readText())
        assertEquals(emptyList<String>(), r.errors)
        assertEquals(282, r.rows.size)
        val first = r.rows.first()
        assertEquals(LocalDate.of(2026, 10, 5), first.date)
        assertEquals("Services", first.category)
        assertEquals(-2499L, first.signedCents)
        assertEquals("Internet + phone", first.note)
        assertEquals("Telekom", first.seller)
        assertTrue(r.rows.any { it.seller == "Café Central" }) // UTF-8 survives
        assertTrue(r.rows.any { it.account == "Bank" && it.signedCents > 0 })
    }

    @Test fun roundTripsQuotedFields() {
        val rows = listOf(
            CsvRow(LocalDate.of(2026, 1, 2), "Eating Out", -1234, "Pizza, beer and \"tip\"", "Personal"),
            CsvRow(LocalDate.of(2026, 1, 3), "Salary", 250000, "", "Bank"),
        )
        assertEquals(rows, Csv.parse(Csv.write(rows)).rows)
    }

    @Test fun toleratesUnquotedCommasInNote() {
        val r = Csv.parse("Date, Category, Amount, Note, Account\n1/5/2026, Shopping, -3.00, milk, eggs, Personal\n")
        assertEquals("milk, eggs".replace(" ", ""), r.rows.single().note.replace(" ", ""))
        assertEquals("Personal", r.rows.single().account)
    }
}

class PeriodTest {
    @Test fun shiftsAcrossYearBoundary() {
        val jan = Period(PeriodType.MONTH, 2026, 1)
        assertEquals(Period(PeriodType.MONTH, 2025, 12), jan.shift(-1))
        assertEquals(LocalDate.of(2026, 2, 1), jan.endExclusive)
        assertEquals(LocalDate.of(2027, 1, 1), jan.withType(PeriodType.YEAR).endExclusive)
    }
}

class SellerMatcherTest {
    private fun rule(id: Long, type: MatchType, pattern: String, position: Int, category: Long? = id) =
        SellerRule(id, type, pattern, useLastMatched = false, categoryId = category, position = position, updatedAt = 0)

    @Test fun exactIgnoresCaseAndSpacing() {
        assertTrue(SellerMatcher.matches(MatchType.EXACT, "Kavarna Rog", "  KAVARNA   rog "))
        assertTrue(!SellerMatcher.matches(MatchType.EXACT, "Kavarna Rog", "Kavarna Rog 2"))
    }

    @Test fun containsMatchesWholeWordsOnly() {
        assertTrue(SellerMatcher.matches(MatchType.CONTAINS, "lidl", "LIDL Ljubljana 123"))
        assertTrue(SellerMatcher.matches(MatchType.CONTAINS, "costa coffee", "Costa  Coffee Airport"))
        assertTrue(!SellerMatcher.matches(MatchType.CONTAINS, "spar", "Sparkasse"))
        assertTrue(SellerMatcher.matches(MatchType.CONTAINS, "Žito", "pekarna žito d.o.o."))
    }

    @Test fun regexIsCaseInsensitiveAndInvalidNeverMatches() {
        assertTrue(SellerMatcher.matches(MatchType.REGEX, "^(hofer|lidl)\\b", "HOFER 123"))
        assertTrue(!SellerMatcher.matches(MatchType.REGEX, "(", "anything"))
        assertTrue(SellerMatcher.regexError("(") != null)
        assertNull(SellerMatcher.regexError("^a+$"))
    }

    @Test fun topmostRuleWins() {
        val rules = listOf(
            rule(1, MatchType.CONTAINS, "spar", position = 1),
            rule(2, MatchType.EXACT, "Spar Ljubljana", position = 0),
        )
        assertEquals(2L, SellerMatcher.firstMatch(rules, "SPAR LJUBLJANA")?.id)
        assertEquals(1L, SellerMatcher.firstMatch(rules, "Spar Maribor")?.id)
        assertNull(SellerMatcher.firstMatch(rules, "Mercator"))
    }
}

class CsvSellerTest {
    @Test fun sellerColumnRoundTrips() {
        val rows = listOf(CsvRow(LocalDate.of(2026, 10, 6), "Eating Out", -280, "with Ana", "Personal", "Kavarna Rog"))
        val text = Csv.write(rows)
        assertTrue(text.startsWith("Date, Category, Amount, Note, Account, Seller\n"))
        assertEquals(rows, Csv.parse(text).rows)
    }

    @Test fun sixColumnFileToleratesCommasInNote() {
        val r = Csv.parse("Date, Category, Amount, Note, Account, Seller\n1/5/2026, Shopping, -3.00, milk, eggs, Personal, Lidl\n")
        assertEquals("Lidl", r.rows.single().seller)
        assertEquals("Personal", r.rows.single().account)
    }

    @Test fun oldFiveColumnFileHasNoSeller() {
        val r = Csv.parse("Date, Category, Amount, Note, Account\n1/5/2026, Shopping, -3.00, milk, Personal\n")
        assertEquals("", r.rows.single().seller)
    }
}
