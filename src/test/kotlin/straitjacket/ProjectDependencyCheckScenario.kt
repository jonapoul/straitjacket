package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.buildGradleKts
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.trimmedOutputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache

// The other direction of ProjectDependencyScenario: the catalog sits below the project version, so
// forcing stays out of it and only the check has an opinion. The okio violation gives the check
// real work to report, which stops the assertion passing vacuously.
class ProjectDependencyCheckScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        include(":sub")
      """
        .trimIndent()
    )

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      dependencies {
        implementation(project(":sub"))
        implementation("com.squareup.okio:okio:3.16.0")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      sub = { module = "com.example:sub", version = "1.0.0" }
      okio = { module = "com.squareup.okio:okio", version = "3.6.0" }
      """
        .trimIndent()
    )

    "sub" {
      buildGradleKts(
        """
        plugins {
          kotlin("jvm")
        }

        group = "com.example"
        version = "2.0.0"
        """
          .trimIndent()
      )
    }
  }

  @Test
  fun `a project resolving above its catalog entry is not reported as a violation`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains(
        """
        com.squareup.okio:okio:3.6.0 -> 3.16.0 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)
        """
          .trimIndent()
      )
      .outputDoesNotContain("com.example:sub")
  }
}
