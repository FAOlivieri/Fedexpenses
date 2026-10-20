package io.github.faolivieri.fedexpenses

import io.github.faolivieri.fedexpenses.data.IncomingKind
import io.github.faolivieri.fedexpenses.data.ShareItem
import io.github.faolivieri.fedexpenses.data.ShareParticipant
import io.github.faolivieri.fedexpenses.data.SharePayment
import io.github.faolivieri.fedexpenses.data.Settlement
import io.github.faolivieri.fedexpenses.data.SharePayload
import io.github.faolivieri.fedexpenses.data.Shares
import io.github.faolivieri.fedexpenses.data.Transaction
import io.github.faolivieri.fedexpenses.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SharesTest {
    private val day = 24L * 3600 * 1000
    private val dayOf: (Long) -> LocalDate = { LocalDate.ofEpochDay(it / day) }

    private fun item(id: String = "s1", ver: Int = 0, ts: Long = 100 * day, total: Long = 6000) = ShareItem(
        shareId = id, version = ver, timestamp = ts, totalCents = total, seller = "Gostilna Šestica", note = "Dinner",
        people = listOf(
            ShareParticipant("fede-id", "Federico", 3334),
            ShareParticipant(null, "Ana", 3333),
            ShareParticipant("marko-id", "Marko", 3333),
        ),
        to = 1, paidBy = 0,
    )

    private val payload = SharePayload(
        "fede-id", "Federico", listOf(item(), item(id = "s2", ver = 3, total = 1250)),
        payments = listOf(SharePayment("p1", 2, 101 * day, 4000, fromSender = false, note = "Bank transfer")),
        startingCents = -1500,
    )

    private fun pay(id: String = "p1", ver: Int = 0, ts: Long = 100 * day, cents: Long = 4000, fromSender: Boolean = false) =
        SharePayment(id, ver, ts, cents, fromSender, "")

    private fun settlement(id: Long, person: Long, cents: Long, fromMe: Boolean, ts: Long, shareId: String? = null, ver: Int = 0) =
        Settlement(id = id, personId = person, timestamp = ts, amountCents = cents, fromMe = fromMe, accountId = 1,
            shareId = shareId, shareVersion = ver)

    private fun tx(id: Long, cents: Long, ts: Long, shareId: String? = null, ver: Int = 0) = Transaction(
        id = id, timestamp = ts, amountCents = cents, type = TxType.EXPENSE, categoryId = 1, accountId = 1,
        shareId = shareId, shareVersion = ver,
    )

    @Test fun jsonRoundTrips() {
        assertEquals(payload, Shares.fromJson(Shares.toJson(payload)))
    }

    @Test fun sharesWithoutSettleUpsOrStartingBalanceStillRead() {
        val plain = SharePayload("fede-id", "Federico", listOf(item()))
        assertEquals(plain, Shares.fromJson(Shares.toJson(plain)))
    }

    @Test fun classifiesSettleUpsFromTheRecipientsSide() {
        val sender = 7L
        val existing = listOf(
            settlement(1, sender, 9999, fromMe = true, ts = 100 * day, shareId = "p1", ver = 0),
            // The sender paid the recipient 40.00: on this phone that's "they paid me" (fromMe = false).
            settlement(2, sender, 4000, fromMe = false, ts = 101 * day),
            settlement(3, sender, 2500, fromMe = true, ts = 100 * day),
        )
        val kinds = Shares.classifyPayments(listOf(
            pay("p1", ver = 0),
            pay("p1", ver = 1),
            pay("p2", cents = 4000, fromSender = true),
            pay("p3", cents = 2500, fromSender = true),
            pay("p4", cents = 4000, fromSender = true, ts = 105 * day),
        ), existing, sender, dayOf).map { it.kind }
        assertEquals(
            listOf(IncomingKind.ALREADY_ADDED, IncomingKind.UPDATE, IncomingKind.POSSIBLE_DUPLICATE, IncomingKind.NEW, IncomingKind.NEW),
            kinds,
        )
        assertEquals(IncomingKind.NEW, Shares.classifyPayments(listOf(pay("p2", fromSender = true)), existing, null, dayOf).single().kind)
    }

    @Test fun otherFilesAreRejected() {
        for (text in listOf("""{"format":"expenses-backup","version":6}""", "not json", "")) {
            assertEquals(text, true, runCatching { Shares.fromJson(text) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun partsMustAddUpToTheWhole() {
        val bad = payload.copy(items = listOf(item().copy(people = item().people.map { it.copy(basisPoints = 1000) })))
        Shares.fromJson(Shares.toJson(bad))
    }

    @Test(expected = IllegalArgumentException::class)
    fun recipientMustBeAParticipant() {
        Shares.fromJson(Shares.toJson(payload.copy(items = listOf(item().copy(to = 5)))))
    }

    @Test fun recipientPartOfAnEqualThreeWaySplit() {
        assertEquals(2000L, item().recipientCents)
        assertEquals(417L, item(total = 1250).recipientCents)
    }

    @Test fun classifiesAgainstExistingTransactions() {
        val existing = listOf(
            tx(1, 9999, 100 * day, shareId = "s1", ver = 0),
            tx(2, 1250, 101 * day),
            tx(3, 4000, 100 * day),
        )
        val items = listOf(
            item(id = "s1", ver = 0),
            item(id = "s1", ver = 2),
            item(id = "s9", total = 1250),
            item(id = "s8", total = 4000, ts = 103 * day),
            item(id = "s7", total = 777),
        )
        val kinds = Shares.classify(items, existing, dayOf).map { it.kind }
        assertEquals(
            listOf(IncomingKind.ALREADY_ADDED, IncomingKind.UPDATE, IncomingKind.POSSIBLE_DUPLICATE, IncomingKind.NEW, IncomingKind.NEW),
            kinds,
        )
    }

    @Test fun alreadySharedTransactionsAreNotDuplicates() {
        val existing = listOf(tx(1, 6000, 100 * day, shareId = "other"))
        assertEquals(IncomingKind.NEW, Shares.classify(listOf(item()), existing, dayOf).single().kind)
    }
}
