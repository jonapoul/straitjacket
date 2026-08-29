package straitjacket

import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.settingsGradleKts
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache

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
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Configuration cache entry stored.")

    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Reusing configuration cache.")
  }
}
