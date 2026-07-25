package straitjacket.internal

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
 * behind a build that does the opposite of what was asked for. So it throws, from inside the
 * transform, meaning whoever reads the value is who the build fails for.
 */
private fun <T : Any> ProviderFactory.strictProperty(
  name: String,
  expected: String,
  parse: (String) -> T?,
): Provider<T> =
  gradleProperty(name).map { value ->
    parse(value)
      ?: throw InvalidPropertyError(
        "Gradle property '$name' is set to '$value', which is not $expected."
      )
  }

/**
 * An [Error] rather than a [org.gradle.api.GradleException], because
 * DefaultDependencySubstitutionApplicator runs substitution rules through Try.ofFailable, which
 * catches Exception and hangs it on the edge as an unresolved dependency. Artifact resolution
 * reports that, but the resolution result walk the check tasks do says nothing, so a typo in
 * logForcedVersions, only ever read from inside a rule, went unnoticed by straitjacketCheck. An
 * Error isn't an Exception, so it goes straight past. Yes, it's a bit ugly, I know.
 */
private class InvalidPropertyError(message: String) : Error(message)
