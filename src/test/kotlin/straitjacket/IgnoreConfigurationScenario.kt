package straitjacket

import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.taskSucceeded
import blueprint.test.trimmedOutputContains
import blueprint.test.withGradleProperty
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache

/**
 * A dependency newer than the catalog lives only in a custom resolvable configuration, so whether
 * the check fails depends entirely on whether that configuration is listed in
 * [StraitjacketExtension.ignoredConfigurations]. The `ignore` Gradle property toggles it.
 */
class IgnoreConfigurationScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      val custom by configurations.creating { isCanBeResolved = true }

      straitjacket {
        if (providers.gradleProperty("ignore").map { it.toBoolean() }.getOrElse(false)) {
          ignoredConfigurations.add("custom")
        }
      }

      dependencies {
        custom("com.squareup.okio:okio:3.16.4")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `a newer dependency in an ignored configuration does not fail the check`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .withGradleProperty(name = "ignore", value = true)
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
  }

  @Test
  fun `a newer dependency in a non-ignored configuration fails the check`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .withGradleProperty(name = "ignore", value = false)
      .failsBuild()
      .trimmedOutputContains("com.squareup.okio:okio:3.16.0 -> 3.16.4 (in custom)")
  }
}
