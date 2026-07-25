package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.outputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.withoutConfigurationCache

// Forcing replaces the selector a dependency is headed for, and a selector carries more than
// coordinates. A platform is a plain module with Category=platform on its selector, so a
// replacement built from coordinates alone stops being a platform and resolution goes looking for a
// library variant the BOM does not have. Test fixtures and feature variants carry the same kind of
// state as capability selectors.
class ForcePlatformScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      dependencies {
        implementation(platform("com.squareup.okhttp3:okhttp-bom:4.9.0"))
        // Versionless, so the version it resolves to is the one the platform dictates, which makes
        // it a witness for the platform surviving as a platform.
        implementation("com.squareup.okhttp3:okhttp")
      }

      tasks.register("printResolvedOkhttp") {
        doLast {
          configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents {
            val mv = moduleVersion
            if (mv != null && mv.name == "okhttp") {
              logger.lifecycle("RESOLVED_OKHTTP=" + mv.version)
            }
          }
        }
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      okhttpBom = { module = "com.squareup.okhttp3:okhttp-bom", version = "4.12.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `a platform forced up to the catalog version is still a platform`() = runScenario {
    // The BOM is requested at 4.9.0 and the catalog declares 4.12.0, so the platform is forced up
    // and okhttp comes out at the version the newer BOM constrains it to. Resolving a configuration
    // in a doLast block is not configuration-cache compatible, so this fixture task opts out of the
    // cache the harness enables by default.
    assertThatTask(":printResolvedOkhttp")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKHTTP=4.12.0")
  }
}
