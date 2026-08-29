package straitjacket

import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.tasksSucceeded
import blueprint.test.tasksWereSkipped
import blueprint.test.trimmedOutputContains
import blueprint.test.withGradleProperty
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache

class CheckScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      $$"""
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      val okioVersion by properties

      straitjacket {
        enabled = providers.gradleProperty("straitjacketEnabled").map { it.toBoolean() }.getOrElse(true)
      }

      dependencies {
        implementation("com.squareup.okio:okio:$okioVersion")
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
  fun `a directly requested older version passes the check`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .withGradleProperty(name = "okioVersion", value = "3.6.0")
      .buildsSuccessfully()
      .tasksSucceeded(":straitjacketCheckLibs", ":straitjacketCheck")
  }

  @Test
  fun `a directly requested newer version fails the check`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .withGradleProperty(name = "okioVersion", value = "3.16.4")
      .failsBuild()
      .trimmedOutputContains(
        "> Task :straitjacketCheckLibs FAILED",
        """
        FAILURE: Build failed with an exception.

        * What went wrong:
        Execution failed for task ':straitjacketCheckLibs' (registered by plugin 'dev.jonpoulton.straitjacket').
        > Straitjacket found dependencies resolved to versions newer than the version catalog declares:

            com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)

          Update your version catalog, or exclude them with ignoredModules or ignoredConfigurations.
        """
          .trimIndent(),
      )
  }

  @Test
  fun `disabling the plugin skips the checks even for a newer version`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .withGradleProperty(name = "okioVersion", value = "3.16.4")
      .withGradleProperty(name = "straitjacketEnabled", value = false)
      .buildsSuccessfully()
      .tasksWereSkipped(":straitjacketCheckLibs", ":straitjacketCheck")
  }
}
