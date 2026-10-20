package io.github.faolivieri.fedexpenses.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType { EXPENSE, INCOME }

enum class TxSource { MANUAL, NOTIFICATION, IMPORT, RECURRING, SHARED }

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
    indices = [Index("timestamp"), Index("categoryId"), Index("accountId"), Index("recurrenceId"), Index("shareId")],
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
    /** The [Person] who paid a split expense; null when you paid. */
    val paidBy: Long? = null,
    /** Identifies a shared expense on every phone it was sent to; set when first shared or received. */
    val shareId: String? = null,
    /** Edit counter of a shared expense: a received copy with a higher version replaces the older one. */
    @ColumnInfo(defaultValue = "0") val shareVersion: Int = 0,
) {
    val signedCents: Long get() = if (type == TxType.EXPENSE) -amountCents else amountCents
}

/** Someone you split expenses with. [remoteId] identifies their app once they have shared something with you. */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val remoteId: String? = null,
    /**
     * Balance carried over from before you tracked it here (e.g. from another app): positive when
     * they owe you, negative when you owe them. Counts in the balance only, never in accounts or reports.
     */
    val startingBalanceCents: Long = 0,
)

/** One participant's part of a split transaction. [personId] null is you. */
@Entity(
    tableName = "splits",
    foreignKeys = [
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("transactionId"), Index("personId")],
)
data class SplitPart(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val personId: Long?,
    /** Hundredths of a percent: 10000 is the whole amount. */
    val basisPoints: Int,
)

/** Money paid between you and a person to even out what you owe each other. Not an expense or income. */
@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"]),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"]),
    ],
    indices = [Index("personId"), Index("accountId")],
)
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val timestamp: Long,
    /** Always positive; [fromMe] gives the direction. */
    val amountCents: Long,
    /** True: you paid the person. False: they paid you. */
    val fromMe: Boolean,
    val accountId: Long,
    val note: String = "",
    /** Identifies a shared settle-up on every phone it was sent to; set when first shared or received. */
    val shareId: String? = null,
    /** Edit counter of a shared settle-up: a received copy with a higher version replaces the older one. */
    @ColumnInfo(defaultValue = "0") val shareVersion: Int = 0,
)

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
    /** Note given to detected payments this rule matches, if any. */
    val note: String? = null,
)

/**
 * How to recognise payment notifications from one app. Each pattern field holds one regex per
 * line, matched against "title\ntext"; it captures `amount` and optionally `merchant` (falls back
 * to the notification title). Income patterns are tried first: they're usually specific ("Received…"),
 * while expense patterns are often catch-alls that would also match incoming money.
 */
@Entity(tableName = "notification_rules")
data class NotificationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String,
    /** Recognises outgoing payments. */
    val expensePattern: String,
    val expenseEnabled: Boolean = true,
    /** Recognises incoming money. */
    val incomePattern: String = "",
    val incomeEnabled: Boolean = false,
    val accountId: Long,
    /** Switches the whole app on or off. */
    val enabled: Boolean = true,
) {
    /** The patterns in use, in the order they're tried. */
    val activePatterns: List<Pair<TxType, String>>
        get() = buildList {
            if (incomeEnabled && incomePattern.isNotBlank()) add(TxType.INCOME to incomePattern)
            if (expenseEnabled && expensePattern.isNotBlank()) add(TxType.EXPENSE to expensePattern)
        }
}

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

/** Transaction joined with its category, account and split, for display. */
data class TxDetails(
    val tx: Transaction,
    val category: Category,
    val account: Account,
    val split: List<SplitPart> = emptyList(),
) {
    /** Your part of the amount; the whole amount unless the transaction is split. */
    val myCents: Long get() = Splits.mine(tx, split)
    val mySignedCents: Long get() = if (tx.type == TxType.EXPENSE) -myCents else myCents
}

fun normalizeMerchant(raw: String): String =
    raw.trim().lowercase().replace(Regex("\\s+"), " ")
