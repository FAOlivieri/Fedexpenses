package io.github.faolivieri.fedexpenses.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Category::class,
        Account::class,
        Transaction::class,
        SellerRule::class,
        Recurrence::class,
        NotificationRule::class,
        ProcessedNotification::class,
        CapturedNotification::class,
        Person::class,
        SplitPart::class,
        Settlement::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun sellerRules(): SellerRuleDao
    abstract fun recurrences(): RecurrenceDao
    abstract fun rules(): RuleDao
    abstract fun notificationLog(): NotificationLogDao
    abstract fun backup(): BackupDao
    abstract fun sharing(): SharingDao

    companion object {
        const val NAME = "expenses.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /**
         * v1 remembered one category per exact seller name. Each memory becomes an "exact match,
         * last matched" seller rule, so behavior is unchanged; most recently used first.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SELLER_RULES_CREATE)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seller_rules_position` ON `seller_rules` (`position`)")

                // Recover the seller's original spelling (v1 stored it lowercased).
                val spelling = HashMap<String, String>()
                db.query("SELECT merchant FROM transactions WHERE merchant IS NOT NULL ORDER BY timestamp").use { c ->
                    while (c.moveToNext()) c.getString(0)?.let { spelling[normalizeMerchant(it)] = it.trim() }
                }
                db.query("SELECT merchantKey, categoryId, updatedAt FROM merchant_categories ORDER BY updatedAt DESC").use { c ->
                    var position = 0
                    while (c.moveToNext()) {
                        val key = c.getString(0)
                        db.execSQL(
                            "INSERT INTO seller_rules (matchType, pattern, useLastMatched, categoryId, position, updatedAt) " +
                                "VALUES ('EXACT', ?, 1, ?, ?, ?)",
                            arrayOf<Any>(spelling[key] ?: key, c.getLong(1), position++, c.getLong(2)),
                        )
                    }
                }
                db.execSQL("DROP TABLE merchant_categories")

                // v1 copied the seller into the note. Seller is now shown on its own, so clear
                // notes that are still just that copy.
                db.execSQL("UPDATE transactions SET note = '' WHERE merchant IS NOT NULL AND TRIM(note) = TRIM(merchant)")
            }
        }

        /** v3 adds repeating transactions. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(RECURRENCES_CREATE)
                db.execSQL("ALTER TABLE transactions ADD COLUMN recurrenceId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurrenceId` ON `transactions` (`recurrenceId`)")
            }
        }

        /**
         * v4: watched apps get separate expense and income patterns (the old single pattern keeps
         * its direction), and seller rules get an optional note.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(NOTIFICATION_RULES_V4_CREATE)
                db.execSQL(
                    "INSERT INTO notification_rules_v4 (id, name, packageName, expensePattern, expenseEnabled, " +
                        "incomePattern, incomeEnabled, accountId, enabled) " +
                        "SELECT id, name, packageName, " +
                        "CASE WHEN type = 'INCOME' THEN '' ELSE pattern END, CASE WHEN type = 'INCOME' THEN 0 ELSE 1 END, " +
                        "CASE WHEN type = 'INCOME' THEN pattern ELSE '' END, CASE WHEN type = 'INCOME' THEN 1 ELSE 0 END, " +
                        "accountId, enabled FROM notification_rules"
                )
                db.execSQL("DROP TABLE notification_rules")
                db.execSQL("ALTER TABLE notification_rules_v4 RENAME TO notification_rules")
                db.execSQL("ALTER TABLE seller_rules ADD COLUMN note TEXT")
            }
        }

        /** v5 adds people, split transactions and settle-ups. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN paidBy INTEGER")
                SHARING_CREATE.forEach(db::execSQL)
            }
        }

        private val SHARING_CREATE = listOf(
            "CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nickname` TEXT NOT NULL, `remoteId` TEXT)",
            "CREATE TABLE IF NOT EXISTS `splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, `personId` INTEGER, `basisPoints` INTEGER NOT NULL, " +
                "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_splits_transactionId` ON `splits` (`transactionId`)",
            "CREATE INDEX IF NOT EXISTS `index_splits_personId` ON `splits` (`personId`)",
            "CREATE TABLE IF NOT EXISTS `settlements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`personId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, " +
                "`fromMe` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_settlements_personId` ON `settlements` (`personId`)",
            "CREATE INDEX IF NOT EXISTS `index_settlements_accountId` ON `settlements` (`accountId`)",
        )

        private const val NOTIFICATION_RULES_V4_CREATE = "CREATE TABLE IF NOT EXISTS `notification_rules_v4` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
            "`expensePattern` TEXT NOT NULL, `expenseEnabled` INTEGER NOT NULL, `incomePattern` TEXT NOT NULL, " +
            "`incomeEnabled` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)"

        private const val RECURRENCES_CREATE = "CREATE TABLE IF NOT EXISTS `recurrences` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `frequency` TEXT NOT NULL, `startDay` INTEGER NOT NULL, " +
            "`nextIndex` INTEGER NOT NULL, `endDay` INTEGER, `amountCents` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
            "`categoryId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `note` TEXT NOT NULL, `merchant` TEXT)"

        private const val SELLER_RULES_CREATE = "CREATE TABLE IF NOT EXISTS `seller_rules` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `matchType` TEXT NOT NULL, `pattern` TEXT NOT NULL, " +
            "`useLastMatched` INTEGER NOT NULL, `categoryId` INTEGER, `position` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL)"
    }
}
