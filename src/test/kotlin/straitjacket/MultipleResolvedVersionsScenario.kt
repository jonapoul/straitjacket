package straitjacket

import blueprint.test.buildGradleKts
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.trimmedOutputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache

/**
 * When a module resolves to more than one version above the catalog version, every offending
 * version gets its own violation line. Collapsing a module to the highest version it resolved to
 * used to hide the others completely.
 */
class MultipleResolvedVersionsScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      // Both requests sit above the catalog version 3.0.0, so neither is forced, and the classpaths
      // split: the compile classpaths resolve 3.16.0 and the runtime classpaths resolve 3.16.4.
      dependencies {
        implementation("com.squareup.okio:okio:3.16.0")
        runtimeOnly("com.squareup.okio:okio:3.16.4")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.0.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `every version resolved above the catalog version is reported`() = runScenario {
    assertThatTaskWithConfigurationCache(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains(
        "> Task :straitjacketCheckLibs FAILED",
        "com.squareup.okio:okio:3.0.0 -> 3.16.0 (in compileClasspath, testCompileClasspath)",
        "com.squareup.okio:okio:3.0.0 -> 3.16.4 (in runtimeClasspath, testRuntimeClasspath)",
      )
  }
}
