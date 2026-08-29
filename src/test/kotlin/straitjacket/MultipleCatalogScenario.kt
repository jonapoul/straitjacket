package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.trimmedOutputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

class MultipleCatalogScenario : StraitjacketScenarioTest() {
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

      dependencies {
        implementation("com.squareup.okio:okio:3.16.4")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
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
  fun `a dependency newer than a non-ignored catalog declares fails the check`() = runScenario {
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

          Update your version catalog, or exclude them with ignoredModules or ignoredConfigurations.
        """
          .trimIndent()
      )
  }
}
