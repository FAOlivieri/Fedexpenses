package io.github.faolivieri.fedexpenses.data

import java.util.concurrent.ConcurrentHashMap

/** Matches seller names against [SellerRule]s. Matching ignores case and extra spaces. */
object SellerMatcher {
    /** Compiled CONTAINS/REGEX patterns; a failed Result marks an invalid regex. */
    private val cache = ConcurrentHashMap<String, Result<Regex>>()

    fun matches(type: MatchType, pattern: String, seller: String): Boolean {
        if (pattern.isBlank() || seller.isBlank()) return false
        return when (type) {
            MatchType.EXACT -> normalizeMerchant(pattern) == normalizeMerchant(seller)
            MatchType.CONTAINS -> compile(type, pattern).getOrNull()?.containsMatchIn(normalizeMerchant(seller)) == true
            MatchType.REGEX -> compile(type, pattern).getOrNull()?.containsMatchIn(seller.trim()) == true
        }
    }

    fun matches(rule: SellerRule, seller: String): Boolean = matches(rule.matchType, rule.pattern, seller)

    /** The rule that decides [seller]'s category: the topmost one that matches. */
    fun firstMatch(rules: List<SellerRule>, seller: String): SellerRule? =
        rules.sortedWith(compareBy({ it.position }, { it.id })).firstOrNull { matches(it, seller) }

    /** Why [pattern] is not a valid regular expression, or null if it is. */
    fun regexError(pattern: String): String? =
        compile(MatchType.REGEX, pattern).exceptionOrNull()?.message?.lineSequence()?.firstOrNull()

    private fun compile(type: MatchType, pattern: String): Result<Regex> = cache.getOrPut("$type|$pattern") {
        runCatching {
            when (type) {
                // Whole words or phrases only: "spar" matches "SPAR Ljubljana" but not "Sparkasse".
                MatchType.CONTAINS ->
                    Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(normalizeMerchant(pattern)) + "(?![\\p{L}\\p{N}])",
                        RegexOption.IGNORE_CASE)
                else -> Regex(pattern, RegexOption.IGNORE_CASE)
            }
        }
    }
}
