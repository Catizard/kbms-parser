package io.github.catizard.kbms.parser

import kotlin.experimental.and

fun parseXX(base: Int, x1: Char, x2: Char): Int? = if (base == 62) parseInt62(x1, x2) else parseInt36(x1, x2)

fun parseInt36(x1: Char, x2: Char): Int? {
    return (conv36(x1) ?: return null) * 36 + (conv36(x2) ?: return null)
}

fun parseInt62(x1: Char, x2: Char): Int? {
    return (conv62(x1) ?: return null) * 62 + (conv62(x2) ?: return null)
}

private fun conv36(c: Char): Int? {
    return when (c) {
        in '0'..'9' -> {
            c - '0'
        }

        in 'a'..'z' -> {
            c - 'a' + 10
        }

        in 'A'..'Z' -> {
            c - 'A' + 10
        }

        else -> null
    }
}

private fun conv62(c: Char): Int? {
    return when (c) {
        in '0'..'9' -> {
            c - '0'
        }

        in 'A'..'Z' -> {
            c - 'A' + 10
        }

        in 'a'..'z' -> {
            c - 'a' + 36
        }

        else -> null
    }
}

/**
 * TODO: Use pre-calculated table instead of [Character.forDigit]
 */
fun convertHexString(data: ByteArray): String {
    val sb = StringBuilder(data.size * 2)
    for (b in data) {
        sb.append(Character.forDigit(b.rotateRight(4).and(0xf).toInt(), 16))
        sb.append(Character.forDigit(b.and(0xf).toInt(), 16))
    }
    return sb.toString()
}