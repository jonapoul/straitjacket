package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.outputDoesNotContain
import blueprint.test.taskSucceeded
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.trimmedOutputContains

/**
 * Two modules resolve newer than the catalog declares. The `ignore` Gradle property picks the
 * pattern added to [StraitjacketExtension.ignoredModules], so each test can tell which of the two
 * the pattern covered by which one is left in the report.
 */
class IgnoreModuleScenario : StraitjacketScenarioTest() {
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
        implementation("com.squareup.okio:okio:3.16.4")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      okhttp = { module = "com.squareup.okhttp3:okhttp", version = "4.11.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `both modules are reported when nothing is ignored`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains(
        "com.squareup.okhttp3:okhttp:4.11.0 -> 4.12.0",
        "com.squareup.okio:okio:3.16.0 -> 3.16.4",
      )
  }

  @Test
  fun `an exact coordinate ignores only that module`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Pignore=com.squareup.okio:okio")
      .failsBuild()
      .trimmedOutputContains("com.squareup.okhttp3:okhttp:4.11.0 -> 4.12.0")
      .outputDoesNotContain("com.squareup.okio")
  }

  @Test
  fun `a wildcard name ignores every module in the group`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Pignore=com.squareup.okio:*")
      .failsBuild()
      .trimmedOutputContains("com.squareup.okhttp3:okhttp:4.11.0 -> 4.12.0")
      .outputDoesNotContain("com.squareup.okio")
  }

  @Test
  fun `a wildcard group ignores the module whatever its group`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Pignore=*:okio")
      .failsBuild()
      .trimmedOutputContains("com.squareup.okhttp3:okhttp:4.11.0 -> 4.12.0")
      .outputDoesNotContain("com.squareup.okio")
  }

  @Test
  fun `a wildcard covering both modules passes the check`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Pignore=com.squareup.*")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
  }

  /**
   * A dot in a pattern is matched literally rather than as a regex wildcard, so this pattern covers
   * neither module and both are still reported.
   */
  @Test
  fun `dots in a pattern are not treated as wildcards`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Pignore=com.squareup.okioX:okio")
      .failsBuild()
      .trimmedOutputContains(
        "com.squareup.okhttp3:okhttp:4.11.0 -> 4.12.0",
        "com.squareup.okio:okio:3.16.0 -> 3.16.4",
      )
  }
}
