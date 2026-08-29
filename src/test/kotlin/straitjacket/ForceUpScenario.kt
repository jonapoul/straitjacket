package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

class ForceUpScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.6.0")
      }

      // Resolves runtimeClasspath and prints the version okio actually resolved to, so the test can
      // assert that Straitjacket forced the request up rather than only that the check passed.
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
  fun `a dependency requested below the catalog version is forced up to the catalog version`() =
    runScenario {
      // okio is requested directly at 3.6.0 but the catalog declares 3.16.0, so resolution must
      // force it up to 3.16.0. Resolving a configuration in a doLast block is not
      // configuration-cache compatible, so this fixture task opts out of the cache that the
      // harness enables by default.
      assertThatTask(":printResolvedOkio")
        .withoutConfigurationCache()
        .buildsSuccessfully()
        .outputContains("RESOLVED_OKIO=3.16.0")
        .outputDoesNotContain("RESOLVED_OKIO=3.6.0")
    }
}
