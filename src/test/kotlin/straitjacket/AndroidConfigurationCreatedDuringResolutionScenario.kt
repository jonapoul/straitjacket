package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.settingsGradleKts
import kotlin.test.Test
import straitjacket.test.COMPILE_SDK
import straitjacket.test.MIN_SDK
import straitjacket.test.RequiresAndroidSdk
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.androidLocalProperties

/**
 * The real-AGP twin of [ConfigurationCreatedDuringResolutionScenario], which reproduces the same
 * bug with a hand-rolled hook and explains it. Read that one first.
 *
 * The chain, read off a stack trace taken where the configuration is added:
 * ```
 * DefaultConfiguration.runDependencyActions          <- androidHostTestCompileClasspath resolving
 *   KotlinTestMultiplatformDependencyHandler.maybeAddTestDependencyCapability
 *     DefaultDomainObjectCollection.addLater         <- a dependency backed by a task provider
 *       AndroidUnitTest$CreationAction.configure     <- so the task gets realised and configured
 *         BootClasspathConfigImpl.getMockableJarArtifact -> getAndroidJar
 *           configurations.maybeCreate("androidApis")
 * ```
 *
 * Every part of the build script below is load-bearing, each one arrived at by watching a simpler
 * version pass while the bug was still there:
 * - `com.android.kotlin.multiplatform.library`, not `com.android.library`, which never takes this
 *   path.
 * - `withHostTest`, which is what gives the target an `androidHostTestCompileClasspath` to resolve.
 * - `kotlin("test")` in `commonTest`, which looks like noise and is not: it is the dependency
 *   `maybeAddTestDependencyCapability` reacts to.
 *
 * If a later AGP stops creating that configuration mid-resolution this one goes quiet, and the
 * synthetic scenario is what keeps holding the line. It skips where there is no Android SDK.
 */
@RequiresAndroidSdk
class AndroidConfigurationCreatedDuringResolutionScenario : StraitjacketScenarioTest() {
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
          withHostTest {}
        }

        sourceSets.commonMain.dependencies {
          implementation("com.squareup.okio:okio:3.6.0")
        }

        sourceSets.commonTest.dependencies {
          implementation(kotlin("test"))
        }
      }
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
  fun `the check survives AGP creating androidApis mid-resolution`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Configuration cache entry stored.")
  }
}
