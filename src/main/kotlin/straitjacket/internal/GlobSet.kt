package straitjacket.internal

internal class GlobSet(patterns: Set<String>) {
  private val literals: Set<String>
  private val wildcards: List<Regex>

  init {
    val (globs, exact) = patterns.partition { '*' in it }
    literals = exact.toSet()
    wildcards = globs.map { it.toGlobRegex() }
  }

  operator fun contains(value: String): Boolean =
    value in literals || wildcards.any { regex -> regex.matches(value) }

  /**
   * Everything outside a `*` is matched literally, so a pattern like `com.squareup.okio:*` cannot
   * have its dots read as regex wildcards.
   */
  private fun String.toGlobRegex(): Regex =
    split("*").joinToString(separator = ".*", transform = Regex::escape).toRegex()
}
