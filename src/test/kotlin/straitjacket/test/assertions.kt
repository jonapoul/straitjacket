package straitjacket.test

import assertk.Assert
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import blueprint.test.taskSkipped
import kotlin.DeprecationLevel.ERROR
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

@Deprecated("trimmedOutputContains needs at least one expected string", level = ERROR)
@Suppress("unused", "UnusedReceiverParameter")
internal fun Assert<BuildResult>.trimmedOutputContains(): Assert<BuildResult> =
  throw UnsupportedOperationException("trimmedOutputContains needs at least one string")

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

fun Assert<BuildResult>.tasksSkipped(vararg names: String): Assert<BuildResult> =
  names.fold(this) { assertion, name -> assertion.taskSkipped(name) }

@Deprecated("tasksSkipped needs at least one expected string", level = ERROR)
@Suppress("unused", "UnusedReceiverParameter")
fun Assert<BuildResult>.tasksSkipped(): Assert<BuildResult> =
  throw UnsupportedOperationException("tasksSkipped needs at least one string")

fun Assert<String>.contains(expected: String): Assert<String> = transform { actual ->
  assertThat(actual).contains(expected)
  actual
}

fun Assert<String>.doesNotContain(expected: String): Assert<String> = transform { actual ->
  assertThat(actual).doesNotContain(expected)
  actual
}
