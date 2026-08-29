package straitjacket.test

import assertk.Assert
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import blueprint.test.Scenario
import blueprint.test.assertThatTask
import blueprint.test.withConfigurationCache
import org.gradle.testkit.runner.GradleRunner

internal fun Scenario.assertThatTaskWithConfigurationCache(task: String): Assert<GradleRunner> =
  assertThatTask(task).withConfigurationCache()

internal fun Assert<String>.contains(expected: String): Assert<String> = transform { actual ->
  assertThat(actual).contains(expected)
  actual
}

internal fun Assert<String>.doesNotContain(expected: String): Assert<String> = transform { actual ->
  assertThat(actual).doesNotContain(expected)
  actual
}
