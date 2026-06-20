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
