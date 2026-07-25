package straitjacket.internal

/**
 * A set of patterns matched against a string, where `*` stands for any run of characters.
 *
 * Patterns without a `*` are kept in a set and matched by lookup rather than by regex. The
 * resolution rule asks these questions once per dependency per catalog per resolution, and the
 * overwhelmingly common case is a handful of literal names, so that case must not pay for a regex.
 *
 * Compiled once, at construction. Callers hold one of these behind a `finalizeValueOnRead` property
 * so the compilation happens once per build rather than once per question. See
 * [straitjacket.StraitjacketPlugin].
 */
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
