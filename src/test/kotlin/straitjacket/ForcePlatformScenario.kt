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

// A platform is a plain module with Category=platform on its selector, so a replacement built from
// coordinates alone goes looking for a library variant the BOM does not have. Test fixtures carry
// the same kind of state as capability selectors.
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
        // Versionless, so the version it resolves to is whatever the platform dictates
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
    // The BOM is forced from 4.9.0 up to the catalog's 4.12.0, so okhttp comes out at what the
    // newer BOM constrains it to. Resolving in doLast is not configuration-cache compatible.
    assertThatTask(":printResolvedOkhttp")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKHTTP=4.12.0")
  }
}
