package straitjacket.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/** Reads a Gradle property as `true` or `false`, and fails the build if it is anything else. */
internal fun ProviderFactory.booleanProperty(name: String): Provider<Boolean> =
  strictProperty(
    name,
    expected = "a boolean (true or false)",
    parse = String::toBooleanStrictOrNull,
  )

/** Reads a Gradle property as a [LogLevel] name, matched without regard to case. */
internal fun ProviderFactory.logLevelProperty(name: String): Provider<LogLevel> =
  strictProperty(
    name,
    expected = "a log level (${LogLevel.entries.joinToString(", ") { it.name }})",
  ) { value ->
    LogLevel.entries.firstOrNull { level -> level.name.equals(value, ignoreCase = true) }
  }

/**
 * A value that doesn't parse is a typo, and quietly falling back to the extension value hides it
 * behind a build that does the opposite of what was asked for. So it throws, on read, from inside
 * the provider chain the value is asked for through.
 */
private fun <T : Any> ProviderFactory.strictProperty(
  name: String,
  expected: String,
  parse: (String) -> T?,
): Provider<T> =
  gradleProperty(name).map { value ->
    parse(value)
      ?: throw InvalidUserDataException(
        "Gradle property '$name' is set to '$value', which is not $expected."
      )
  }
