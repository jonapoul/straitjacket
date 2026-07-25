package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.failsBuild
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.trimmedOutputContains

/**
 * FAILING TEST - documents a known bug, not yet fixed.
 *
 * ## The bug
 *
 * The `(in ...)` list in a violation line names every configuration the module appeared in, not the
 * configurations that actually resolved the offending version. When a module resolves to different
 * versions in different configurations, the report claims configurations are affected when they
 * aren't.
 *
 * ## Where
 *
 * `internal/tasks.kt`, `buildResolvedVersionMap`. It builds two independent maps keyed on
 * `"$group:$name"`:
 * - `versions[key]` keeps the *highest* version seen across all matching configurations.
 * - `configs[key]` accumulates *every* configuration name the module appeared in, regardless of
 *   which version resolved there.
 *
 * They are then zipped together at the end, so the highest version gets paired with the full set of
 * configuration names. `StraitjacketCheck.execute` formats that pair straight into the violation
 * line as `"$coordinate:$catalogVersion -> $resolvedVersion (in $configNameStr)"`.
 *
 * ## This scenario
 *
 * The catalog declares okio 3.16.0. `implementation` requests exactly the catalog version, and
 * `runtimeOnly` requests a newer 3.16.4. That splits the classpaths:
 * - `compileClasspath` / `testCompileClasspath` resolve 3.16.0, matching the catalog. Not a
 *   violation.
 * - `runtimeClasspath` / `testRuntimeClasspath` resolve 3.16.4, above the catalog. A violation.
 *
 * Note that the forcing side is not involved here. `restrictions.kt` only forces requests that are
 * *below* the catalog version, and neither request is.
 *
 * ## Expected vs actual
 *
 * The assertion below is what the report *should* say. What it actually prints today is:
 * ```
 * com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)
 * ```
 * naming the two compile classpaths even though they resolved the correct 3.16.0. Anyone acting on
 * that report would go looking for a compile-classpath problem that doesn't exist, and
 * `ignoredConfigurations` advice based on it would be wrong too.
 *
 * ## Suggested fix
 *
 * Key the collected data on version as well as coordinate, so a module resolving to several
 * versions across configurations is tracked per version rather than collapsed to the maximum. That
 * also lets the report list every offending version rather than only the highest, which is arguably
 * a second bug hiding behind this one: if a module resolved to two distinct versions that are both
 * above the catalog, only the higher is reported today.
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
  fun `the violation names only the configurations that resolved the newer version`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains("> Task :straitjacketCheckLibs FAILED")
      .trimmedOutputContains(
        "com.squareup.okio:okio:3.16.0 -> 3.16.4 (in runtimeClasspath, testRuntimeClasspath)"
      )
  }
}
