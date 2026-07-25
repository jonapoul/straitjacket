package straitjacket.test

import assertk.Assert
import assertk.assertions.contains
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

// To avoid line indent issues when asserting outputContains
internal fun Assert<BuildResult>.trimmedOutputContains(expected: String) = transform { result ->
  assertThat(result.output.trimLines(), name = "output").contains(expected)
  result
}

internal fun Assert<BuildResult>.trimmedOutputContains(
  vararg expected: String
): Assert<BuildResult> =
  expected.fold(this) { assertion, value -> assertion.trimmedOutputContains(value) }

// A no-arg call would silently assert nothing, so make it a compile error. This overload is more
// specific than the vararg one, so it wins resolution and reports the error.
@Deprecated(
  "trimmedOutputContains needs at least one expected string",
  level = DeprecationLevel.ERROR,
)
@Suppress("unused")
internal fun Assert<BuildResult>.trimmedOutputContains(): Assert<BuildResult> =
  throw UnsupportedOperationException("trimmedOutputContains needs at least one expected string")

internal fun Assert<GradleRunner>.plusArguments(vararg args: String): Assert<GradleRunner> =
  transform { runner ->
    runner.withArguments(runner.arguments + args)
  }

internal fun Assert<GradleRunner>.withoutConfigurationCache(): Assert<GradleRunner> =
  plusArguments("--no-configuration-cache")

internal fun Assert<GradleRunner>.withGradleProperty(
  name: String,
  value: Any,
): Assert<GradleRunner> = plusArguments("-P$name=$value")
