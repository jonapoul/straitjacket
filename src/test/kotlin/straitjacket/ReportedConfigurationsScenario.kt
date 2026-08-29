package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.trimmedOutputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * The `(in ...)` list of a violation must name only the configurations that actually resolved the
 * offending version, not every configuration the module appeared in.
 *
 * `buildResolvedVersionMap` used to keep the highest version seen for a module in one map and every
 * configuration it appeared in in another, then pair the two. A module resolving to different
 * versions in different configurations therefore had its highest version reported against the full
 * set of configuration names, sending anyone reading the report - or writing an
 * `ignoredConfigurations` entry off the back of it - after configurations that were fine.
 */
class ReportedConfigurationsScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      // Deliberately splits the classpaths: the compile classpaths resolve the catalog version
      // 3.16.0, the runtime classpaths resolve 3.16.4 via conflict resolution against runtimeOnly.
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
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `the violation names only the configurations that resolved the newer version`() =
    runScenario {
      assertThatTask(":straitjacketCheck")
        .failsBuild()
        .trimmedOutputContains(
          "> Task :straitjacketCheckLibs FAILED",
          "com.squareup.okio:okio:3.16.0 -> 3.16.4 (in runtimeClasspath, testRuntimeClasspath)",
        )
    }
}
