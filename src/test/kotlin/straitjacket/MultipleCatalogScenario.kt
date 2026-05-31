package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.ScenarioTest
import blueprint.test.assertThatTask
import blueprint.test.failsBuild
import kotlin.test.Test
import straitjacket.test.GRADLE_VERSION

class MultipleCatalogScenario : ScenarioTest() {
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

      dependencies {
        implementation("com.squareup.okio:okio:3.16.4")
      }
      """
        .trimIndent()
    )

    ("gradle" / "libs.versions.toml")(
      """
        [libraries]
      """
    )

    ("gradle" / "someOtherLibs.versions.toml")(
      """
        [libraries]
        okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
    )
  }

  @Test
  fun `non-ignored catalog fails check when dependency is newer`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains(
        """
        > Task :straitjacketCheckSomeOtherLibs FAILED

        FAILURE: Build failed with an exception.

        * What went wrong:
        Execution failed for task ':straitjacketCheckSomeOtherLibs' (registered by plugin 'dev.jonpoulton.straitjacket').
        > Straitjacket found dependencies resolved to versions newer than the version catalog declares:

            com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)

          Update your version catalog or add these configurations to ignoredConfigurations.
        """
          .trimIndent()
      )
  }
}
