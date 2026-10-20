package io.github.faolivieri.fedexpenses.data

import io.github.faolivieri.fedexpenses.util.toLocalDate
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A participant of a shared expense as the sender knows them. [id] is their sharing id, if known. */
data class ShareParticipant(val id: String?, val name: String, val basisPoints: Int)

/** One shared expense, addressed to one recipient. [to] and [paidBy] are indexes into [people]. */
data class ShareItem(
    val shareId: String,
    val version: Int,
    val timestamp: Long,
    val totalCents: Long,
    val seller: String?,
    val note: String,
    val people: List<ShareParticipant>,
    val to: Int,
    val paidBy: Int,
) {
    /** Each participant's part in cents, keyed by index into [people]. */
    val parts: Map<Long?, Long>
        get() = Splits.parts(totalCents,
            people.mapIndexed { i, p -> SplitPart(transactionId = 0, personId = i.toLong(), basisPoints = p.basisPoints) },
            paidBy.toLong())

    val recipientCents: Long get() = parts[to.toLong()] ?: 0
}

/** A settle-up between the sender and the recipient. [fromSender]: the sender paid the recipient. */
data class SharePayment(
    val shareId: String,
    val version: Int,
    val timestamp: Long,
    val amountCents: Long,
    val fromSender: Boolean,
    val note: String,
)

/**
 * What one phone sends to one person. [startingCents] is the sender's starting balance with the recipient
 * (positive: the recipient owes the sender), or null if it isn't included.
 */
data class SharePayload(
    val fromId: String,
    val fromName: String,
    val items: List<ShareItem>,
    val payments: List<SharePayment> = emptyList(),
    val startingCents: Long? = null,
) {
    val isEmpty get() = items.isEmpty() && payments.isEmpty() && startingCents == null
}

/** What to do with a received item. LINK attaches it to the possible duplicate instead of adding it. */
enum class ImportChoice { ADD, LINK, SKIP }

enum class IncomingKind { NEW, UPDATE, ALREADY_ADDED, POSSIBLE_DUPLICATE }

/** A received item and how it relates to what's already on this phone. */
data class Incoming(val item: ShareItem, val kind: IncomingKind, val existing: Transaction? = null)

/** A received settle-up and how it relates to what's already on this phone. */
data class IncomingPayment(val payment: SharePayment, val kind: IncomingKind, val existing: Settlement? = null)

/** A received share compared with this phone's data. [sender] is null the first time they share. */
data class SharePreview(val items: List<Incoming>, val payments: List<IncomingPayment>, val sender: Person?)

object Shares {
    const val FORMAT = "fedexpenses-share"
    fun toJson(p: SharePayload): String = JSONObject().apply {
        put("format", FORMAT)
        put("v", 1)
        put("from", JSONObject().put("id", p.fromId).put("name", p.fromName))
        put("items", JSONArray(p.items.map { i ->
            JSONObject().put("id", i.shareId).put("ver", i.version).put("ts", i.timestamp).put("total", i.totalCents)
                .put("seller", i.seller ?: JSONObject.NULL).put("note", i.note)
                .put("people", JSONArray(i.people.map {
                    JSONObject().put("id", it.id ?: JSONObject.NULL).put("name", it.name).put("bp", it.basisPoints)
                }))
                .put("to", i.to).put("paid", i.paidBy)
        }))
        put("payments", JSONArray(p.payments.map {
            JSONObject().put("id", it.shareId).put("ver", it.version).put("ts", it.timestamp).put("amount", it.amountCents)
                .put("fromSender", it.fromSender).put("note", it.note)
        }))
        p.startingCents?.let { put("starting", it) }
    }.toString()

    /** Reads and validates a payload; throws [IllegalArgumentException] if it isn't a valid share. */
    fun fromJson(s: String): SharePayload {
        val o = runCatching { JSONObject(s) }.getOrElse { throw IllegalArgumentException("Not a Fedexpenses share") }
        require(o.optString("format") == FORMAT) { "Not a Fedexpenses share" }
        require(o.optInt("v") == 1) { "Shared by a newer version of Fedexpenses; update the app" }
        val from = o.getJSONObject("from")
        val items = o.getJSONArray("items")
        return SharePayload(
            fromId = from.getString("id").also { require(it.isNotBlank()) },
            fromName = from.optString("name").trim().ifEmpty { "Someone" },
            items = (0 until items.length()).map { n ->
                val i = items.getJSONObject(n)
                val people = i.getJSONArray("people").let { a ->
                    (0 until a.length()).map { k ->
                        val p = a.getJSONObject(k)
                        ShareParticipant(if (p.isNull("id")) null else p.getString("id"), p.getString("name").trim(), p.getInt("bp"))
                    }
                }
                ShareItem(
                    shareId = i.getString("id"), version = i.getInt("ver"), timestamp = i.getLong("ts"),
                    totalCents = i.getLong("total"), seller = if (i.isNull("seller")) null else i.getString("seller"),
                    note = i.optString("note"), people = people, to = i.getInt("to"), paidBy = i.getInt("paid"),
                ).also { item ->
                    require(item.shareId.isNotBlank() && item.totalCents in 1..100_000_000_000L)
                    require(people.isNotEmpty() && people.all { it.basisPoints in 0..Splits.WHOLE })
                    require(people.sumOf { it.basisPoints } == Splits.WHOLE)
                    require(item.to in people.indices && item.paidBy in people.indices)
                }
            },
            payments = o.optJSONArray("payments")?.let { a ->
                (0 until a.length()).map { n ->
                    val i = a.getJSONObject(n)
                    SharePayment(
                        shareId = i.getString("id"), version = i.getInt("ver"), timestamp = i.getLong("ts"),
                        amountCents = i.getLong("amount"), fromSender = i.getBoolean("fromSender"), note = i.optString("note"),
                    ).also { require(it.shareId.isNotBlank() && it.amountCents in 1..100_000_000_000L) }
                }
            }.orEmpty(),
            startingCents = if (o.has("starting")) o.getLong("starting") else null,
        )
    }

    /**
     * Compares received settle-ups with this phone's: same [Settlement.shareId] means already added (or an
     * update, if the received version is newer); otherwise an unshared settle-up with [senderId] for the same
     * amount and direction within a day of it is a possible duplicate.
     */
    fun classifyPayments(
        payments: List<SharePayment>, existing: List<Settlement>, senderId: Long?,
        dayOf: (Long) -> LocalDate = { it.toLocalDate() },
    ): List<IncomingPayment> = payments.map { p ->
        val same = existing.firstOrNull { it.shareId == p.shareId }
        when {
            same != null -> IncomingPayment(p, if (p.version > same.shareVersion) IncomingKind.UPDATE else IncomingKind.ALREADY_ADDED, same)
            else -> {
                val day = dayOf(p.timestamp)
                val dup = existing.firstOrNull {
                    it.shareId == null && it.personId == senderId && it.amountCents == p.amountCents &&
                        it.fromMe == !p.fromSender && abs(ChronoUnit.DAYS.between(dayOf(it.timestamp), day)) <= 1
                }
                if (dup != null) IncomingPayment(p, IncomingKind.POSSIBLE_DUPLICATE, dup) else IncomingPayment(p, IncomingKind.NEW)
            }
        }
    }

    /**
     * Compares received items with this phone's transactions: same [Transaction.shareId] means already
     * added (or an update, if the received version is newer); otherwise an unshared expense with the same
     * amount within a day of it is a possible duplicate.
     */
    fun classify(
        items: List<ShareItem>, existing: List<Transaction>, dayOf: (Long) -> LocalDate = { it.toLocalDate() },
    ): List<Incoming> = items.map { item ->
        val same = existing.firstOrNull { it.shareId == item.shareId }
        when {
            same != null -> Incoming(item, if (item.version > same.shareVersion) IncomingKind.UPDATE else IncomingKind.ALREADY_ADDED, same)
            else -> {
                val day = dayOf(item.timestamp)
                val dup = existing.firstOrNull {
                    it.shareId == null && it.type == TxType.EXPENSE && it.amountCents == item.totalCents &&
                        abs(ChronoUnit.DAYS.between(dayOf(it.timestamp), day)) <= 1
                }
                if (dup != null) Incoming(item, IncomingKind.POSSIBLE_DUPLICATE, dup) else Incoming(item, IncomingKind.NEW)
            }
        }
    }
}
