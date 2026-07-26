package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.taskSucceeded
import kotlin.test.Test
import straitjacket.test.COMPILE_SDK
import straitjacket.test.MIN_SDK
import straitjacket.test.PRINT_RESOLVED_OKIO
import straitjacket.test.RequiresAndroidSdk
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.androidLocalProperties
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.trimmedOutputContains
import straitjacket.test.withoutConfigurationCache

/**
 * A `com.android.library` module, the shape most consumers apply Straitjacket to. Nothing in the
 * plugin knows about AGP, so what this pins is that both halves reach the per-variant classpaths
 * AGP adds rather than only the plain `compileClasspath` and `runtimeClasspath` a JVM project has.
 *
 * [AndroidApplicationScenario] is the same scenario for an application module and
 * [AndroidKmpScenario] for a KMP one. It skips where there is no Android SDK.
 */
@RequiresAndroidSdk
class AndroidLibraryScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    androidLocalProperties()

    buildGradleKts(
      """
      plugins {
        id("com.android.library")
        id("dev.jonpoulton.straitjacket")
      }

      android {
        namespace = "straitjacket.test"
        compileSdk = $COMPILE_SDK
        defaultConfig {
          minSdk = $MIN_SDK
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
          "debugCompileClasspath",
          "debugRuntimeClasspath",
          "releaseCompileClasspath",
          "releaseRuntimeClasspath",
        )
    }
}
