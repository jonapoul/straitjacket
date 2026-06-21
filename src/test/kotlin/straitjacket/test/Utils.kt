package straitjacket.test

internal fun String.trimLines() = trimIndent().lines().joinToString("\n") { it.trimEnd() }
