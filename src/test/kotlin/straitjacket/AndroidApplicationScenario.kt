package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.taskSucceeded
import blueprint.test.trimmedOutputContains
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.COMPILE_SDK
import straitjacket.test.MIN_SDK
import straitjacket.test.PRINT_RESOLVED_OKIO
import straitjacket.test.RequiresAndroidSdk
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.androidLocalProperties

/**
 * [AndroidLibraryScenario] for an application module. An application resolves a runtime classpath
 * it packages rather than one it publishes, and the build types it declares are its own, so the set
 * of configurations both halves have to reach is not the library one. Read that scenario first.
 *
 * The extra `staging` build type is what shows the plugin does not only see the two AGP creates
 * itself. It skips where there is no Android SDK.
 */
@RequiresAndroidSdk
class AndroidApplicationScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    androidLocalProperties()

    buildGradleKts(
      """
      plugins {
        id("com.android.application")
        id("dev.jonpoulton.straitjacket")
      }

      android {
        namespace = "straitjacket.test"
        compileSdk = $COMPILE_SDK
        defaultConfig {
          minSdk = $MIN_SDK
        }
        buildTypes {
          create("staging") {
            initWith(getByName("debug"))
          }
        }
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.6.0")
      }

      $PRINT_RESOLVED_OKIO
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

  @Test
  fun `the check passes`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      .outputContains("Configuration cache entry stored.")
  }

  @Test
  fun `every variant classpath is forced up to the catalog version`() = runScenario {
    // Resolving a configuration in a doLast block is not configuration-cache compatible, so this
    // fixture task opts out of the cache the harness enables by default.
    assertThatTask(":printResolvedOkio")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .trimmedOutputContains(
        "RESOLVED_OKIO=debugCompileClasspath:3.16.0",
        "RESOLVED_OKIO=debugRuntimeClasspath:3.16.0",
        "RESOLVED_OKIO=releaseCompileClasspath:3.16.0",
        "RESOLVED_OKIO=releaseRuntimeClasspath:3.16.0",
        "RESOLVED_OKIO=stagingCompileClasspath:3.16.0",
        "RESOLVED_OKIO=stagingRuntimeClasspath:3.16.0",
      )
      .outputDoesNotContain(":3.6.0")
  }

  @Test
  fun `a version resolving above the catalog is reported against the variant configurations`() =
    runScenario {
      // Drop the catalog below what okio is requested at, so nothing is forced and the request
      // resolves above the catalog.
      rootDir
        .resolve("gradle/libs.versions.toml")
        .writeText(
          """
          [libraries]
          okio = { module = "com.squareup.okio:okio", version = "3.5.0" }
          """
            .trimIndent()
        )

      // The full `(in ...)` list is every resolvable configuration AGP registers, which is not
      // worth pinning here, so this only asserts the per-variant ones are among them.
      assertThatTask(":straitjacketCheck")
        .failsBuild()
        .trimmedOutputContains(
          "> Task :straitjacketCheckLibs FAILED",
          "com.squareup.okio:okio:3.5.0 -> 3.6.0 (in ",
          "debugRuntimeClasspath",
          "releaseRuntimeClasspath",
          "stagingRuntimeClasspath",
        )
    }
}
