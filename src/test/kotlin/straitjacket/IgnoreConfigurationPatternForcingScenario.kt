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
import straitjacket.test.withoutConfigurationCache

/**
 * okio is requested below the catalog in a custom configuration, so whether Straitjacket forces it
 * up says whether the pattern in [StraitjacketExtension.ignoredConfigurations] covered that
 * configuration.
 *
 * The forcing half of what [IgnoreConfigurationPatternScenario] covers for checking. The two halves
 * read the pattern at different times, so both need proving: the rule tests the name inside the
 * substitution rule, where the property is final.
 */
class IgnoreConfigurationPatternForcingScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      val debugUnitTestRuntime by configurations.creating { isCanBeResolved = true }

      straitjacket {
        val pattern = providers.gradleProperty("ignore").orNull
        if (pattern != null) {
          ignoredConfigurations.add(pattern)
        }
      }

      dependencies {
        debugUnitTestRuntime("com.squareup.okio:okio:3.6.0")
      }

      // Prints the version okio actually resolved to, so the test asserts on forcing rather than
      // on the check task.
      tasks.register("printResolvedOkio") {
        doLast {
          configurations.getByName("debugUnitTestRuntime").incoming.resolutionResult.allComponents {
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
  fun `a configuration matched by a pattern is not forced up`() = runScenario {
    // Resolving in doLast is not configuration cache compatible, so opt out of the harness default
    assertThatTask(":printResolvedOkio", "-Pignore=*UnitTestRuntime")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.6.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.16.0")
  }

  @Test
  fun `a configuration no pattern covers is still forced up`() = runScenario {
    assertThatTask(":printResolvedOkio", "-Pignore=*AndroidTest*")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.16.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.6.0")
  }
}
