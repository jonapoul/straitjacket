package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.withGradleProperty
import straitjacket.test.withoutConfigurationCache

/**
 * A dependency older than the catalog lives only in a custom resolvable configuration, so whether
 * Straitjacket forces it up depends entirely on whether that configuration is listed in
 * [StraitjacketExtension.ignoredConfigurations]. The `ignore` Gradle property toggles it.
 *
 * The forcing half of what [IgnoreConfigurationScenario] covers for checking.
 */
class IgnoreConfigurationForcingScenario : StraitjacketScenarioTest() {
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
        custom("com.squareup.okio:okio:3.6.0")
      }

      // Prints the version okio actually resolved to, so the test asserts on forcing rather than
      // on the check task.
      tasks.register("printResolvedOkio") {
        doLast {
          configurations.getByName("custom").incoming.resolutionResult.allComponents {
            val mv = moduleVersion
            if (mv != null && mv.group == "com.squareup.okio" && mv.name == "okio") {
              logger.lifecycle("RESOLVED_OKIO=" + mv.version)
            }
          }
        }
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
  fun `an older dependency in an ignored configuration is not forced up`() = runScenario {
    // Resolving in doLast is not configuration cache compatible, so opt out of the harness default
    assertThatTask(":printResolvedOkio")
      .withoutConfigurationCache()
      .withGradleProperty("ignore", true)
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.6.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.16.0")
  }

  @Test
  fun `an older dependency in a non-ignored configuration is forced up`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withoutConfigurationCache()
      .withGradleProperty("ignore", false)
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.16.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.6.0")
  }
}
