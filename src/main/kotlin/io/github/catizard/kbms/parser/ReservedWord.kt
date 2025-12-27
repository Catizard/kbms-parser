package io.github.catizard.kbms.parser

/**
 * [ReservedWord] is a helper class for matching the reserved words in a string
 *
 * TODO: Use a trie to speed up match process
 */
class ReservedWord {
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
    word.forEachIndexed { index, ch ->
        if (ch.lowercase() != this[index + 1].lowercase()) {
            return null
        }
    }
    return this.substring(word.length + 2).trim()
}