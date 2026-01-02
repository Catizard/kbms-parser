package io.github.catizard.kbms.parser

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * [ReservedWord] is a helper class for matching the reserved words in a string
 *
 * TODO: Due to the design issue, we cannot have a pair of reserved word that one is the prefix
 *  of the another one (e.g. foo and foobar, foo is the prefix of foobar)
 */
class ReservedWord<T : ParseContext> {
    companion object {
        val logger = KotlinLogging.logger {}
    }

    private val trie: Array<Array<Int>> = Array(256) { Array(26) { 0 } }
    private val actions: MutableMap<Int, Action<T>> = mutableMapOf()
    private var cn = 0

    interface Action<T> {
        fun execute(ctx: T, arg: String)
    }

    fun interface ParamedAction<T : ParseContext> : Action<T>
    fun interface PlainAction<T : ParseContext> : Action<T>

    fun insert(s: String, action: Action<T>) {
        var root = 0
        for (c in s) {
            val x = c.lowercaseChar() - 'a'
            if (trie[root][x] == 0) {
                trie[root][x] = ++cn
            }
            root = trie[root][x]
        }
        actions[root] = action
    }

    fun executeIfMatched(s: String, ctx: T): Boolean {
        var root = 0
        s.forEachIndexed { i, c ->
            if (c == '#') {
                return@forEachIndexed
            }
            val c = c.lowercaseChar()
            if (c !in 'a'..'z') {
                return false
            }
            val x = c - 'a'
            if (trie[root][x] == 0) {
                return false
            }
            root = trie[root][x]
            actions[root]?.let { action ->
                when (action) {
                    is ParamedAction -> {
                        if (i + 2 >= s.length) {
                            logger.error { "line $s matched the word but it doesn't have enough length to split parameter" }
                        } else {
                            action.execute(ctx, s.substring(i + 2).trim())
                        }
                    }

                    is PlainAction -> action.execute(ctx, s)
                }
                return true
            }
        }
        return false
    }
}

/**
 * Try matching the reserved word and returns the argument
 * Example:
 * ```kotlin
 * "#RANDOM 2".matchReservedWord("RANDOM") // => 2
 * "#RONDAM 2".matchReservedWord("RANDOM") // => null
 * ```
 */
fun String.matchReservedWord(word: String): String? {
    if (this.length <= word.length) {
        return null
    }
    if (word.startsWith("#WAV") || word.startsWith("#BMP")) {
        return ""
    }
    word.forEachIndexed { index, ch ->
        if (ch.lowercase() != this[index + 1].lowercase()) {
            return null
        }
    }
    return this.substring(word.length + 2).trim()
}