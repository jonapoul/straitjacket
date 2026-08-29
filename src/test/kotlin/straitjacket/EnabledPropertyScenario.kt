package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.taskFailed
import blueprint.test.tasksWereSkipped
import blueprint.test.trimmedOutputContains
import blueprint.test.withArgument
import blueprint.test.withGradleProperty
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

class EnabledPropertyScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      straitjacket {
        enabled = false
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.16.4")
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
  fun `the extension value applies when no Gradle property is set`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .tasksWereSkipped(":straitjacketCheckLibs", ":straitjacketCheck")
  }

  @Test
  fun `the Gradle property enables the plugin even when the extension disables it`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty("straitjacket.enabled", true)
      .failsBuild()
      .taskFailed(":straitjacketCheckLibs")
      .trimmedOutputContains(
        """
        > Straitjacket found dependencies resolved to versions newer than the version catalog declares:

            com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)

          Update your version catalog, or exclude them with ignoredModules or ignoredConfigurations.
        """
          .trimIndent()
      )
  }

  @Test
  fun `the Gradle property disables the plugin overriding the extension`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty("straitjacket.enabled", false)
      .buildsSuccessfully()
      .tasksWereSkipped(":straitjacketCheckLibs", ":straitjacketCheck")
  }

  /**
   * A typo here would otherwise leave the extension in charge, so the build would quietly do the
   * opposite of what the property asked for.
   */
  @Test
  fun `a non-boolean string for the property fails the build`() = runScenario {
    listOf("", "not-a-bool", "1", "0", "TRUE", " true", "true ").forEach { enabledProperty ->
      assertThatTask(":straitjacketCheck")
        .withArgument("--rerun-tasks")
        .withGradleProperty(name = "straitjacket.enabled", value = enabledProperty)
        .failsBuild()
        .trimmedOutputContains(
          "Gradle property 'straitjacket.enabled' is set to '$enabledProperty', " +
            "which is not a boolean (true or false)."
        )
    }
  }
}
