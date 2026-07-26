package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
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
 * [AndroidLibraryScenario] for a KMP module with an Android target alongside a JVM one, where the
 * classpaths are per compilation rather than per variant and a dependency declared once in
 * `commonMain` has to be forced in all of them.
 *
 * [AndroidConfigurationCreatedDuringResolutionScenario] uses the same plugin to pin something else
 * entirely, a bug in the check's walk that only AGP's KMP plugin reaches, and deliberately says
 * nothing about forcing. It skips where there is no Android SDK.
 */
@RequiresAndroidSdk
class AndroidKmpScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    androidLocalProperties()

    buildGradleKts(
      """
      plugins {
        kotlin("multiplatform")
        id("com.android.kotlin.multiplatform.library")
        id("dev.jonpoulton.straitjacket")
      }

      kotlin {
        jvm()

        androidLibrary {
          namespace = "straitjacket.test"
          compileSdk = $COMPILE_SDK
          minSdk = $MIN_SDK
        }

        sourceSets.commonMain.dependencies {
          implementation("com.squareup.okio:okio:3.6.0")
        }
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
  fun `every compilation classpath is forced up to the catalog version`() = runScenario {
    // Resolving a configuration in a doLast block is not configuration-cache compatible, so this
    // fixture task opts out of the cache the harness enables by default.
    assertThatTask(":printResolvedOkio")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .trimmedOutputContains(
        "RESOLVED_OKIO=androidCompileClasspath:3.16.0",
        "RESOLVED_OKIO=jvmCompileClasspath:3.16.0",
        "RESOLVED_OKIO=jvmRuntimeClasspath:3.16.0",
      )
      .outputDoesNotContain(":3.6.0")
  }
}
