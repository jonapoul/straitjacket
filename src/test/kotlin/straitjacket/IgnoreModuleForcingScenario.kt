package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.withGradleProperty
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * okio is requested below what the catalog declares, so whether Straitjacket forces it up depends
 * entirely on whether it is listed in [StraitjacketExtension.ignoredModules]. The `ignore` Gradle
 * property supplies the pattern.
 *
 * The forcing half of what [IgnoreModuleScenario] covers for checking. An ignored module is one
 * Straitjacket does not manage at all, so it is left where it was requested rather than only being
 * left out of the report.
 */
class IgnoreModuleForcingScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      straitjacket {
        val pattern = providers.gradleProperty("ignore").orNull
        if (pattern != null) {
          ignoredModules.add(pattern)
        }
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.6.0")
      }

      // Prints the version okio actually resolved to, so the test asserts on forcing rather than
      // on the check task.
      tasks.register("printResolvedOkio") {
        doLast {
          configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents {
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
  fun `an ignored module is not forced up`() = runScenario {
    // Resolving in doLast is not configuration cache compatible, so opt out of the harness default
    assertThatTask(":printResolvedOkio")
      .withGradleProperty(name = "ignore", value = "com.squareup.okio:okio")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.6.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.16.0")
  }

  @Test
  fun `a module ignored by wildcard is not forced up`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withGradleProperty(name = "ignore", value = "com.squareup.*")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.6.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.16.0")
  }

  @Test
  fun `a module no pattern covers is still forced up`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withGradleProperty(name = "ignore", value = "com.squareup.okhttp3:*")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.16.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.6.0")
  }
}
