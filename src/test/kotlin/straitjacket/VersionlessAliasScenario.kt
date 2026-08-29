package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.taskSucceeded
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * A catalog entry may legally declare a module with no version, leaving the version to a platform
 * or to whatever the build asks for. Straitjacket has nothing to pin such a module to, so it must
 * ignore it on both sides: no forcing, and no violation whatever version resolves.
 *
 * The trap is that an empty version is still a `String`. If it reaches the coordinate to version
 * map, the check compares the resolved version against `""`, finds anything greater, and reports a
 * violation with an empty version in it - telling you to update a catalog entry that deliberately
 * has no version to update.
 *
 * The catalog here declares okio without a version and nothing else, so the module resolves purely
 * from what the build requests. That is the only arrangement in which an empty version can reach
 * the map: where a module has other aliases that do declare a version, the highest of those always
 * wins, because an empty version loses every comparison against a real one.
 */
class VersionlessAliasScenario : StraitjacketScenarioTest() {
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

      // Resolving a configuration in a doLast block is not configuration-cache compatible, so the
      // test that uses this task opts out of the cache the harness enables by default.
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
      okio = { module = "com.squareup.okio:okio" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `a module declared without a version does not fail the check`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      // An empty version leaking into the map shows up as a violation with nothing before the
      // arrow.
      .outputDoesNotContain("com.squareup.okio:okio: ->")
  }

  @Test
  fun `a module declared without a version is left at the requested version`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.6.0")
  }
}
