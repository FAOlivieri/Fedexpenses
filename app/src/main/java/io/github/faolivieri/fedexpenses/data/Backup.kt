package io.github.faolivieri.fedexpenses.data

import org.json.JSONArray
import org.json.JSONObject

/** Everything the app stores, as one JSON document. */
data class BackupData(
    val categories: List<Category>,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val sellerRules: List<SellerRule>,
    val rules: List<NotificationRule>,
    val recurrences: List<Recurrence> = emptyList(),
)

object Backup {
    const val FORMAT = "expenses-backup"
    const val VERSION = 3

    fun toJson(d: BackupData): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", System.currentTimeMillis())
        put("categories", JSONArray(d.categories.map {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                .put("color", it.color).put("icon", it.icon).put("isSystem", it.isSystem)
        }))
        put("accounts", JSONArray(d.accounts.map {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("icon", it.icon)
        }))
        put("transactions", JSONArray(d.transactions.map {
            JSONObject().put("id", it.id).put("timestamp", it.timestamp).put("amountCents", it.amountCents)
                .put("type", it.type.name).put("categoryId", it.categoryId).put("accountId", it.accountId)
                .put("note", it.note).put("merchant", it.merchant ?: JSONObject.NULL).put("source", it.source.name)
                .put("recurrenceId", it.recurrenceId ?: JSONObject.NULL)
        }))
        put("sellerRules", JSONArray(d.sellerRules.map {
            JSONObject().put("id", it.id).put("matchType", it.matchType.name).put("pattern", it.pattern)
                .put("useLastMatched", it.useLastMatched).put("categoryId", it.categoryId ?: JSONObject.NULL)
                .put("position", it.position).put("updatedAt", it.updatedAt)
        }))
        put("recurrences", JSONArray(d.recurrences.map {
            JSONObject().put("id", it.id).put("frequency", it.frequency.name).put("startDay", it.startDay)
                .put("nextIndex", it.nextIndex).put("endDay", it.endDay ?: JSONObject.NULL)
                .put("amountCents", it.amountCents).put("type", it.type.name).put("categoryId", it.categoryId)
                .put("accountId", it.accountId).put("note", it.note).put("merchant", it.merchant ?: JSONObject.NULL)
        }))
        put("rules", JSONArray(d.rules.map {
            JSONObject().put("id", it.id).put("name", it.name).put("packageName", it.packageName)
                .put("pattern", it.pattern).put("type", it.type.name).put("accountId", it.accountId)
                .put("enabled", it.enabled)
        }))
    }.toString()

    fun fromJson(s: String): BackupData {
        val o = JSONObject(s)
        require(o.optString("format") == FORMAT) { "Not a Fedexpenses backup file" }
        return BackupData(
            categories = o.getJSONArray("categories").objects().map {
                Category(it.getLong("id"), it.getString("name"), TxType.valueOf(it.getString("type")),
                    it.getInt("color"), it.getString("icon"), it.optBoolean("isSystem"))
            },
            accounts = o.getJSONArray("accounts").objects().map {
                Account(it.getLong("id"), it.getString("name"), it.getInt("color"), it.optString("icon", "wallet"))
            },
            transactions = o.getJSONArray("transactions").objects().map {
                val merchant = if (it.isNull("merchant")) null else it.getString("merchant")
                // Version 1 copied the seller into the note; the seller now has its own field.
                val note = it.optString("note").let { n ->
                    if (o.optInt("version", 1) < 2 && merchant != null && n.trim() == merchant.trim()) "" else n
                }
                Transaction(
                    id = it.getLong("id"), timestamp = it.getLong("timestamp"), amountCents = it.getLong("amountCents"),
                    type = TxType.valueOf(it.getString("type")), categoryId = it.getLong("categoryId"),
                    accountId = it.getLong("accountId"), note = note,
                    merchant = merchant,
                    source = TxSource.valueOf(it.optString("source", TxSource.MANUAL.name)),
                    recurrenceId = if (it.isNull("recurrenceId")) null else it.getLong("recurrenceId"),
                )
            },
            sellerRules = o.optJSONArray("sellerRules")?.objects()?.map {
                SellerRule(
                    id = it.getLong("id"), matchType = MatchType.valueOf(it.getString("matchType")),
                    pattern = it.getString("pattern"), useLastMatched = it.getBoolean("useLastMatched"),
                    categoryId = if (it.isNull("categoryId")) null else it.getLong("categoryId"),
                    position = it.getInt("position"), updatedAt = it.getLong("updatedAt"),
                )
            } ?: legacyMerchants(o),
            rules = o.optJSONArray("rules")?.objects()?.map {
                NotificationRule(it.getLong("id"), it.getString("name"), it.getString("packageName"),
                    it.getString("pattern"), TxType.valueOf(it.getString("type")), it.getLong("accountId"),
                    it.optBoolean("enabled", true))
            } ?: emptyList(),
            recurrences = o.optJSONArray("recurrences")?.objects()?.map {
                Recurrence(
                    id = it.getLong("id"), frequency = Frequency.valueOf(it.getString("frequency")),
                    startDay = it.getLong("startDay"), nextIndex = it.getInt("nextIndex"),
                    endDay = if (it.isNull("endDay")) null else it.getLong("endDay"),
                    amountCents = it.getLong("amountCents"), type = TxType.valueOf(it.getString("type")),
                    categoryId = it.getLong("categoryId"), accountId = it.getLong("accountId"),
                    note = it.optString("note"), merchant = if (it.isNull("merchant")) null else it.getString("merchant"),
                )
            } ?: emptyList(),
        )
    }

    /** Version 1 backups stored exact seller → category memories; they become rules. */
    private fun legacyMerchants(o: JSONObject): List<SellerRule> =
        o.optJSONArray("merchants")?.objects().orEmpty()
            .sortedByDescending { it.getLong("updatedAt") }
            .mapIndexed { i, it ->
                SellerRule(matchType = MatchType.EXACT, pattern = it.getString("merchantKey"), useLastMatched = true,
                    categoryId = it.getLong("categoryId"), position = i, updatedAt = it.getLong("updatedAt"))
            }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
