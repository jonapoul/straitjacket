package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.taskWasSkipped
import blueprint.test.tasksSucceeded
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache

class IgnoreCatalogScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        dependencyResolutionManagement {
          versionCatalogs {
            create("someOtherLibs") {
              from(files("gradle/someOtherLibs.versions.toml"))
            }
          }
        }
      """
        .trimIndent()
    )

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      straitjacket {
        ignoredCatalogs.add("someOtherLibs")
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
      okio = { module = "com.squareup.okio:okio", version = "3.16.4" }
      """
        .trimIndent()
    )

    ("gradle" / "someOtherLibs.versions.toml")(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `a dependency newer than an ignored catalog declares does not fail the check`() =
    runScenario {
      assertThatTaskWithConfigurationCache(":straitjacketCheck")
        .buildsSuccessfully()
        .tasksSucceeded(":straitjacketCheck", ":straitjacketCheckLibs")
        .taskWasSkipped(":straitjacketCheckSomeOtherLibs")
    }
}
