package io.github.faolivieri.fedexpenses.data

object Defaults {
    /** Colors offered in the picker. The first eight are a colorblind-checked categorical order. */
    val palette: List<Int> = listOf(
        0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100,
        0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948,
        0xFF0E8A9A, 0xFF8D5B3F, 0xFF6B7280, 0xFFB5179E,
        0xFF5C7CFA, 0xFF7CB342, 0xFFD4A017, 0xFF37474F,
    ).map { it.toInt() }

    const val UNCATEGORIZED_COLOR = 0xFF8A8A85.toInt()
    const val UNCATEGORIZED_NAME = "Uncategorized"

    const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"

    private const val AMOUNT = "(?<amount>(?:[€$£]|EUR|USD|GBP)\\s?\\d[\\d.,]*|\\d[\\d.,]*\\s?(?:[€$£]|EUR|USD|GBP))"

    /**
     * One pattern per line, tried in order. Covers both layouts Google Wallet has used:
     *  - "€4.50 at Costa Coffee" anywhere in the notification;
     *  - the seller as the title and "€12.50 with Visa ••1234" as the text.
     * The amount must sit next to a currency sign so card digits or promos don't match.
     */
    const val GOOGLE_WALLET_PATTERN =
        "$AMOUNT\\s+(?:at|to|@)\\s+(?<merchant>[^\\n]+)\n" +
            "(?s)^(?<merchant>[^\\n]+)\\n.*?$AMOUNT"

    data class Seed(val name: String, val type: TxType)

    val categories = listOf(
        Seed("Shopping", TxType.EXPENSE),
        Seed("Eating Out", TxType.EXPENSE),
        Seed("Travel", TxType.EXPENSE),
        Seed("Housing", TxType.EXPENSE),
        Seed("Services", TxType.EXPENSE),
        Seed("Medicine", TxType.EXPENSE),
        Seed("Gifts", TxType.EXPENSE),
        Seed("Clothes", TxType.EXPENSE),
        Seed("General", TxType.EXPENSE),
        Seed("Salary", TxType.INCOME),
        Seed("Other Income", TxType.INCOME),
    )

    private val iconKeywords: List<Pair<List<String>, String>> = listOf(
        listOf("salary", "wage", "paycheck", "sueldo") to "payments",
        listOf("income", "refund", "interest", "ingreso") to "savings",
        listOf("eat", "restaurant", "food", "lunch", "comida", "dinner") to "restaurant",
        listOf("coffee", "cafe", "café") to "coffee",
        listOf("date", "bar", "icecream", "ice cream", "drink") to "local_bar",
        listOf("going out", "party", "club", "night") to "nightlife",
        listOf("shop", "grocer", "supermarket", "compra") to "shopping_cart",
        listOf("holiday", "vacation", "vacacion") to "beach",
        listOf("work trip", "business", "work") to "work",
        listOf("travel", "transport", "bus", "train", "taxi", "transfer", "pasaje") to "bus",
        listOf("fuel", "gas", "petrol", "nafta") to "fuel",
        listOf("car", "parking") to "car",
        listOf("house", "housing", "rent", "home", "alquiler") to "home",
        listOf("electric", "power", "luz") to "bolt",
        listOf("service", "internet", "phone", "sim", "subscription") to "wifi",
        listOf("strošk", "bill", "expense", "gasto", "cost") to "receipt",
        listOf("sport", "gym", "fitness") to "fitness",
        listOf("gift", "present", "regalo") to "gift",
        listOf("game") to "games",
        listOf("medic", "health", "pharma", "doctor") to "medical",
        listOf("cloth", "fashion", "ropa") to "checkroom",
        listOf("show", "event", "ticket", "cinema", "concert") to "ticket",
        listOf("kid", "child", "baby") to "child",
        listOf("electronic", "computer", "tech", "gadget") to "devices",
        listOf("sex", "love") to "favorite",
        listOf("pet", "dog", "cat") to "pets",
        listOf("book", "education", "school", "course") to "school",
        listOf("beauty", "hair", "cosmetic") to "spa",
    )

    fun suggestIcon(name: String): String {
        val n = name.lowercase()
        return iconKeywords.firstOrNull { (words, _) -> words.any { it in n } }?.second ?: "category"
    }

    /** Stable color for a new category, cycling the palette in order. */
    fun colorFor(index: Int): Int = palette[index.mod(palette.size)]
}
