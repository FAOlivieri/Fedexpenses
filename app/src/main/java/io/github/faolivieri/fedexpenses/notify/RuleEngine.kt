package io.github.faolivieri.fedexpenses.notify

import io.github.faolivieri.fedexpenses.util.Money
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

sealed interface ParseResult {
    data class Match(val amountCents: Long, val merchant: String) : ParseResult
    data object NoMatch : ParseResult
    data object NoAmount : ParseResult
    data class InvalidPattern(val message: String) : ParseResult
}

/** Applies a [io.github.faolivieri.fedexpenses.data.NotificationRule] pattern to a notification's text. */
object RuleEngine {
    private val cache = ConcurrentHashMap<String, Pattern>()

    /** The exact string a rule's pattern is matched against. */
    fun subject(title: String, text: String): String = "$title\n$text"

    fun compile(pattern: String): Pattern = cache.getOrPut(pattern) { Pattern.compile(pattern) }

    /** [patterns] holds one regex per line; the first one that matches decides the result. */
    fun parse(patterns: String, title: String, text: String): ParseResult {
        val compiled = try {
            patterns.lines().filter { it.isNotBlank() }.map { compile(it.trim()) }
        } catch (e: PatternSyntaxException) {
            return ParseResult.InvalidPattern(e.description ?: "Invalid pattern")
        }
        val subject = subject(title, text)
        val m = compiled.asSequence().map { it.matcher(subject) }.firstOrNull { it.find() }
            ?: return ParseResult.NoMatch
        val amount = m.named("amount")?.let(Money::parseCents)
            ?: return ParseResult.NoAmount
        if (amount <= 0) return ParseResult.NoAmount
        val merchant = m.named("merchant")?.trim()?.takeIf { it.isNotEmpty() } ?: title.trim()
        return ParseResult.Match(amount, merchant)
    }

    private fun Matcher.named(group: String): String? = try {
        group(group)
    } catch (e: IllegalArgumentException) {
        null // the pattern doesn't define this group
    }
}
