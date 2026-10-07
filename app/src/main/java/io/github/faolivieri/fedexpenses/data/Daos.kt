package io.github.faolivieri.fedexpenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isSystem DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM categories WHERE isSystem = 1 LIMIT 1")
    suspend fun getUncategorized(): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    /** Most used categories of a type since [since], for quick-pick notification buttons. */
    @Query(
        """SELECT c.* FROM categories c JOIN transactions t ON t.categoryId = c.id
           WHERE c.isSystem = 0 AND c.type = :type AND t.timestamp >= :since
           GROUP BY c.id ORDER BY COUNT(*) DESC LIMIT :limit"""
    )
    suspend fun mostUsed(type: TxType, since: Long, limit: Int): List<Category>

    @Query("SELECT categoryId AS id, COUNT(*) AS n FROM transactions GROUP BY categoryId")
    fun observeUsageCounts(): Flow<List<IdCount>>
}

data class IdCount(val id: Long, val n: Int)

data class IdSum(val id: Long, val total: Long)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<Account>

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query(
        """SELECT accountId AS id,
           SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END) AS total
           FROM transactions GROUP BY accountId"""
    )
    fun observeBalances(): Flow<List<IdSum>>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC, id DESC")
    fun observeRange(from: Long, to: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<Transaction>

    /** Detected payments, i.e. the ones seller rules apply to. */
    @Query("SELECT * FROM transactions WHERE merchant IS NOT NULL ORDER BY timestamp DESC")
    fun observeWithMerchant(): Flow<List<Transaction>>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long)

    /** Sets [note] on those of [ids] that don't have a note yet. */
    @Query("UPDATE transactions SET note = :note WHERE id IN (:ids) AND TRIM(note) = ''")
    suspend fun setNoteWhereEmpty(ids: List<Long>, note: String)

    @Query("SELECT MIN(timestamp) FROM transactions")
    fun observeEarliest(): Flow<Long?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Transaction?

    @Insert
    suspend fun insert(tx: Transaction): Long

    @Insert
    suspend fun insertAll(txs: List<Transaction>)

    @Update
    suspend fun update(tx: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)

    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from")
    suspend fun reassignAccount(from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    /** "This and future": copies the edited fields onto later payments of the series. */
    @Query(
        """UPDATE transactions SET amountCents = :amountCents, type = :type, categoryId = :categoryId,
           accountId = :accountId, note = :note, merchant = :merchant
           WHERE recurrenceId = :recurrenceId AND timestamp > :after"""
    )
    suspend fun updateLaterInSeries(
        recurrenceId: Long, after: Long, amountCents: Long, type: TxType, categoryId: Long,
        accountId: Long, note: String, merchant: String?,
    )

    @Query("UPDATE transactions SET recurrenceId = NULL WHERE recurrenceId = :recurrenceId")
    suspend fun detachFromSeries(recurrenceId: Long)

    @Query("SELECT MAX(timestamp) FROM transactions WHERE recurrenceId = :recurrenceId")
    suspend fun lastInSeries(recurrenceId: Long): Long?

    @Query("SELECT id FROM transactions WHERE recurrenceId = :recurrenceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestIdInSeries(recurrenceId: Long): Long?
}

@Dao
interface RecurrenceDao {
    @Query("SELECT * FROM recurrences ORDER BY id")
    fun observeAll(): Flow<List<Recurrence>>

    @Query("SELECT * FROM recurrences")
    suspend fun getAll(): List<Recurrence>

    @Query("SELECT * FROM recurrences WHERE id = :id")
    suspend fun get(id: Long): Recurrence?

    @Insert
    suspend fun insert(r: Recurrence): Long

    @Update
    suspend fun update(r: Recurrence)

    @Query("DELETE FROM recurrences WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recurrences SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)

    @Query("UPDATE recurrences SET accountId = :to WHERE accountId = :from")
    suspend fun reassignAccount(from: Long, to: Long)
}

@Dao
interface SellerRuleDao {
    @Query("SELECT * FROM seller_rules ORDER BY position, id")
    fun observeAll(): Flow<List<SellerRule>>

    @Query("SELECT * FROM seller_rules ORDER BY position, id")
    suspend fun getAll(): List<SellerRule>

    @Query("SELECT * FROM seller_rules WHERE id = :id")
    suspend fun get(id: Long): SellerRule?

    @Query("SELECT COALESCE(MAX(position), -1) FROM seller_rules")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(rule: SellerRule): Long

    @Update
    suspend fun update(rule: SellerRule)

    @Update
    suspend fun updateAll(rules: List<SellerRule>)

    @Query("DELETE FROM seller_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE seller_rules SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM notification_rules ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<NotificationRule>>

    @Query("SELECT * FROM notification_rules")
    suspend fun getAll(): List<NotificationRule>

    @Query("SELECT * FROM notification_rules WHERE id = :id")
    suspend fun get(id: Long): NotificationRule?

    @Insert
    suspend fun insert(rule: NotificationRule): Long

    @Update
    suspend fun update(rule: NotificationRule)

    @Query("DELETE FROM notification_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE notification_rules SET accountId = :to WHERE accountId = :from")
    suspend fun reassignAccount(from: Long, to: Long)
}

@Dao
interface NotificationLogDao {
    @Query("SELECT EXISTS(SELECT 1 FROM processed_notifications WHERE `key` = :key)")
    suspend fun isProcessed(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markProcessed(p: ProcessedNotification): Long

    @Query("DELETE FROM processed_notifications WHERE processedAt < :before")
    suspend fun pruneProcessed(before: Long)

    @Insert
    suspend fun capture(c: CapturedNotification): Long

    @Query("DELETE FROM captured_notifications WHERE id NOT IN (SELECT id FROM captured_notifications ORDER BY postedAt DESC LIMIT :keep)")
    suspend fun trimCaptured(keep: Int)

    @Query("SELECT * FROM captured_notifications ORDER BY postedAt DESC")
    fun observeCaptured(): Flow<List<CapturedNotification>>
}

@Dao
interface BackupDao {
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM seller_rules") suspend fun clearSellerRules()
    @Query("DELETE FROM recurrences") suspend fun clearRecurrences()
    @Query("DELETE FROM notification_rules") suspend fun clearRules()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    @Insert suspend fun insertCategories(items: List<Category>)
    @Insert suspend fun insertAccounts(items: List<Account>)
    @Insert suspend fun insertTransactions(items: List<Transaction>)
    @Insert suspend fun insertSellerRules(items: List<SellerRule>)
    @Insert suspend fun insertRecurrences(items: List<Recurrence>)
    @Insert suspend fun insertRules(items: List<NotificationRule>)
}
