package io.github.faolivieri.fedexpenses.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType { EXPENSE, INCOME }

enum class TxSource { MANUAL, NOTIFICATION, IMPORT, RECURRING }

@Entity(tableName = "categories", indices = [Index("name")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxType,
    /** ARGB color. */
    val color: Int,
    /** Key into [io.github.faolivieri.fedexpenses.ui.CategoryIcons]. */
    val icon: String,
    /** System categories (Uncategorized) can't be deleted or retyped. */
    val isSystem: Boolean = false,
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String = "wallet",
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"]),
    ],
    indices = [Index("timestamp"), Index("categoryId"), Index("accountId"), Index("recurrenceId")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis. */
    val timestamp: Long,
    /** Always positive; [type] gives the sign. */
    val amountCents: Long,
    val type: TxType,
    val categoryId: Long,
    val accountId: Long,
    val note: String = "",
    /** Seller (shop, payee). Filled from detected payments, editable by hand; drives seller rules. */
    val merchant: String? = null,
    val source: TxSource = TxSource.MANUAL,
    /** The repeating series this transaction belongs to, if any. */
    val recurrenceId: Long? = null,
) {
    val signedCents: Long get() = if (type == TxType.EXPENSE) -amountCents else amountCents
}

enum class MatchType { EXACT, CONTAINS, REGEX }

/**
 * Picks the category for a detected payment from its seller name. Rules are tried in
 * [position] order and the first match wins.
 */
@Entity(tableName = "seller_rules", indices = [Index("position")])
data class SellerRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchType: MatchType,
    val pattern: String,
    /**
     * True: [categoryId] is learned, i.e. replaced by whatever category the user last gave a
     * payment this rule matched. False: [categoryId] is fixed.
     */
    val useLastMatched: Boolean,
    /** Null when nothing has been learned yet (or the category was deleted): the user is asked. */
    val categoryId: Long?,
    val position: Int,
    val updatedAt: Long,
)

/**
 * How to recognise and parse a payment notification from one app.
 * [pattern] is matched against "title\ntext" and should define the named groups
 * `amount` and optionally `merchant` (falls back to the notification title).
 */
@Entity(tableName = "notification_rules")
data class NotificationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String,
    val pattern: String,
    val type: TxType = TxType.EXPENSE,
    val accountId: Long,
    val enabled: Boolean = true,
)

/** Notifications already turned into transactions, so a rescan never double-counts. */
@Entity(tableName = "processed_notifications")
data class ProcessedNotification(
    @PrimaryKey val key: String,
    val processedAt: Long,
)

/** Small rolling log of notifications from watched apps, used to build and test rules. */
@Entity(tableName = "captured_notifications", indices = [Index("postedAt")])
data class CapturedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val matchedRuleId: Long?,
    val transactionId: Long?,
)

/** Transaction joined with its category and account, for display. */
data class TxDetails(
    val tx: Transaction,
    val category: Category,
    val account: Account,
)

fun normalizeMerchant(raw: String): String =
    raw.trim().lowercase().replace(Regex("\\s+"), " ")
