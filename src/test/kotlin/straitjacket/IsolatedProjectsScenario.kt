package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.gradleProperties
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.taskFailed
import blueprint.test.taskSucceeded
import blueprint.test.trimmedOutputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

class IsolatedProjectsScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    gradleProperties("org.gradle.unsafe.isolated-projects=true")

    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        include(":good", ":bad")
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

    "good" {
      buildGradleKts(
        """
        plugins {
          kotlin("jvm")
          id("dev.jonpoulton.straitjacket")
        }

        dependencies {
          implementation("com.squareup.okio:okio:3.6.0")
        }
        """
          .trimIndent()
      )
    }

    "bad" {
      buildGradleKts(
        """
        plugins {
          kotlin("jvm")
          id("dev.jonpoulton.straitjacket")
        }

        dependencies {
          implementation("com.squareup.okio:okio:3.16.4")
        }
        """
          .trimIndent()
      )
    }
  }

  // Isolated projects reports any cross-project state access as a configuration cache problem,
  // which fails the build. So a build that reaches its assertions at all proves the plugin only
  // ever touched the project it was applied to. The banner assertion is what stops that from being
  // vacuous: an unrecognised gradle.properties key is silently ignored, and the checks would then
  // pass without isolated projects ever having been on.
  @Test
  fun `checks run in every module under isolated projects`() = runScenario {
    assertThatTask("straitjacketCheck")
      .failsBuild()
      .outputContains("Isolated Projects is an incubating feature.")
      .taskSucceeded(":good:straitjacketCheckLibs")
      .taskFailed(":bad:straitjacketCheckLibs")
      .outputDoesNotContain("Configuration cache problems found in this build.")
  }

  @Test
  fun `a violation names only the offending module`() = runScenario {
    assertThatTask(":bad:straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains(
        """
        > Task :bad:straitjacketCheckLibs FAILED

        FAILURE: Build failed with an exception.

        * What went wrong:
        Execution failed for task ':bad:straitjacketCheckLibs' (registered by plugin 'dev.jonpoulton.straitjacket').
        > Straitjacket found dependencies resolved to versions newer than the version catalog declares:

            com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)

          Update your version catalog, or exclude them with ignoredModules or ignoredConfigurations.
        """
          .trimIndent()
      )
  }

  @Test
  fun `a compliant module passes and its configuration cache entry is reusable`() = runScenario {
    assertThatTask(":good:straitjacketCheck")
      .buildsSuccessfully()
      .taskSucceeded(":good:straitjacketCheckLibs")
      .outputContains("Configuration cache entry stored.")

    assertThatTask(":good:straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Reusing configuration cache.")
  }
}
