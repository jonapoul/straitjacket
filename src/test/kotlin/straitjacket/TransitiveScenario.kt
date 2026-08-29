package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.trimmedOutputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

class TransitiveScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      // okhttp 4.12.0 depends transitively on okio 3.6.0. okio is never requested directly here, so
      // this exercises detection of a dependency that a transitive dependency dragged up past the
      // catalog version.
      dependencies {
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.0.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `a dependency dragged above the catalog version by a transitive bump fails the check`() =
    runScenario {
      assertThatTask(":straitjacketCheck")
        .failsBuild()
        .trimmedOutputContains(
          "> Task :straitjacketCheckLibs FAILED",
          "com.squareup.okio:okio:3.0.0 -> 3.6.0",
        )
    }
}
