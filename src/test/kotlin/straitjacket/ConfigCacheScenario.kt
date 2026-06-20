package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.outputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts

class ConfigCacheScenario : StraitjacketScenarioTest() {
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

  // The blueprint test harness already runs every build with --configuration-cache, so reaching a
  // successful assertion proves the store phase produced no configuration cache problems (which
  // fail the build by default). Running the task a second time then proves the entry can be reused.
  @Test
  fun `check is compatible with the configuration cache`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Configuration cache entry stored.")

    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Reusing configuration cache.")
  }
}
