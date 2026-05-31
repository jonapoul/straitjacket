package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.ScenarioTest
import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.taskSkipped
import blueprint.test.taskSucceeded
import kotlin.test.Test
import straitjacket.test.GRADLE_VERSION

class IgnoreCatalogScenario : ScenarioTest() {
  override val gradleVersion = GRADLE_VERSION

  override val fileTree = fileTree {
    "settings.gradle.kts"(
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

    "build.gradle.kts"(
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

    ("gradle" / "libs.versions.toml")(
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
  fun `ignored catalog does not fail check`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      .taskSkipped(":straitjacketCheckSomeOtherLibs")
      .taskSucceeded(":straitjacketCheck")
  }
}
