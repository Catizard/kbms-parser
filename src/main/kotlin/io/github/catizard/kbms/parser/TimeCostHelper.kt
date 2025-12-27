package io.github.catizard.kbms.parser

fun stun(): () -> Long {
    val begin = System.currentTimeMillis()
    return {
        System.currentTimeMillis() - begin
    }
}