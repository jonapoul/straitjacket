package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.taskFailed
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.tasksSkipped
import straitjacket.test.trimmedOutputContains
import straitjacket.test.withGradleProperty

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
      .tasksSkipped(":straitjacketCheckLibs", ":straitjacketCheck")
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
      .tasksSkipped(":straitjacketCheckLibs", ":straitjacketCheck")
  }

  /**
   * A typo here would otherwise leave the extension in charge, so the build would quietly do the
   * opposite of what the property asked for.
   */
  @Test
  fun `a non-boolean string for the property fails the build`() = runScenario {
    listOf("", "not-a-bool", "1", "0", "TRUE", " true", "true ").forEach { enabledProperty ->
      assertThatTask(":straitjacketCheck", "--rerun-tasks")
        .withGradleProperty(name = "straitjacket.enabled", value = enabledProperty)
        .failsBuild()
        .trimmedOutputContains(
          "Gradle property 'straitjacket.enabled' is set to '$enabledProperty', " +
            "which is not a boolean (true or false)."
        )
    }
  }
}
