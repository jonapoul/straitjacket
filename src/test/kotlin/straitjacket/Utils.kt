package straitjacket

import assertk.Assert
import assertk.assertions.contains
import org.gradle.testkit.runner.BuildResult

// To avoid line indent issues when asserting outputContains
internal fun Assert<BuildResult>.trimmedOutputContains(expected: String) = transform { result ->
  assertThat(result.output.trimLines(), name = "output").contains(expected)
  result
}

private fun String.trimLines() = trimIndent().lines().joinToString("\n") { it.trimEnd() }
