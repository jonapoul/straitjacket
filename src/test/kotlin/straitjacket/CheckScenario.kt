package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.ScenarioTest
import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.taskSucceeded
import kotlin.test.Test
import straitjacket.test.GRADLE_VERSION

class CheckScenario : ScenarioTest() {
  override val gradleVersion = GRADLE_VERSION

  override val fileTree = fileTree {
    "settings.gradle.kts"(DEFAULT_REPOSITORIES_KTS)

    "build.gradle.kts"(
      $$"""
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      val okioVersion by properties
      dependencies {
        implementation("com.squareup.okio:okio:$okioVersion")
      }
      """
    )

    ("gradle" / "libs.versions.toml")(
      """
        [libraries]
        okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
    )
  }

  @Test
  fun `directly using an older version succeeds`() = runScenario {
    assertThatTask(":straitjacketCheck", "-PokioVersion=3.6.0")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      .taskSucceeded(":straitjacketCheck")
  }

  @Test
  fun `directly using a newer version fails`() = runScenario {
    assertThatTask(":straitjacketCheck", "-PokioVersion=3.16.4")
      .failsBuild()
      .trimmedOutputContains(
        """
        > Task :straitjacketCheckLibs FAILED

        FAILURE: Build failed with an exception.

        * What went wrong:
        Execution failed for task ':straitjacketCheckLibs'.
        > Straitjacket found dependencies resolved to versions newer than the version catalog declares:

            com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, implementationDependenciesMetadata, runtimeClasspath, testCompileClasspath, testImplementationDependenciesMetadata, testRuntimeClasspath)

          Update your version catalog or add these configurations to ignoredConfigurations.
        """
          .trimIndent()
      )
  }
}
