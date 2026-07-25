package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.taskSucceeded
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.trimmedOutputContains
import straitjacket.test.withoutConfigurationCache

/**
 * A module can be declared under more than one catalog alias with different versions, usually by
 * mistake. The forcing side and the checking side must still agree on what the catalog declares, or
 * Straitjacket fails the build on the very version it forced a moment earlier, telling you to
 * update a catalog that already declares it.
 *
 * Duplicates resolve to the highest declared version, and the decision is made in one place, so
 * neither side depends on the order `libraryAliases` happens to come back in.
 *
 * The catalog below declares the module three times, with the highest version in the *middle* of
 * alias order. That is deliberate: with only two aliases and the highest sorting first, "highest
 * wins" and "first alias wins" agree, and the test cannot tell them apart. Here first-alias-wins
 * would pick 3.6.0 and last-alias-wins would pick 3.9.0, so only picking the highest passes.
 */
class DuplicateAliasScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      $$"""
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      val okioVersion by properties

      dependencies {
        implementation("com.squareup.okio:okio:$okioVersion")
      }

      // Resolving a configuration in a doLast block is not configuration-cache compatible, so the
      // test that uses this task opts out of the cache the harness enables by default.
      tasks.register("printResolvedOkio") {
        doLast {
          configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents {
            val mv = moduleVersion
            if (mv != null && mv.group == "com.squareup.okio" && mv.name == "okio") {
              logger.lifecycle("RESOLVED_OKIO=" + mv.version)
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
      okio-a = { module = "com.squareup.okio:okio", version = "3.6.0" }
      okio-b = { module = "com.squareup.okio:okio", version = "3.16.0" }
      okio-c = { module = "com.squareup.okio:okio", version = "3.9.0" }
      """
        .trimIndent()
    )
  }

  // 3.0.0 is below every version the catalog declares, so the forcing side is guaranteed to act.
  @Test
  fun `the highest version declared for a duplicated module is the one forced`() = runScenario {
    assertThatTask(":printResolvedOkio", "-PokioVersion=3.0.0")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.16.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.6.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.9.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.0.0")
  }

  @Test
  fun `the check passes on the version forced for a duplicated module`() = runScenario {
    assertThatTask(":straitjacketCheck", "-PokioVersion=3.0.0")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
  }

  // 3.16.4 is above every version the catalog declares, so nothing is forced and the check must
  // report a violation. The version it reports has to be the same one the forcing side would have
  // used, so the report names 3.16.0 rather than whichever alias iteration happened to reach last.
  // It has to be a version that really exists: an unresolvable one is absent from the resolution
  // result entirely, so the check would find nothing to report and pass.
  @Test
  fun `a version above every declared version is reported against the highest`() = runScenario {
    assertThatTask(":straitjacketCheck", "-PokioVersion=3.16.4")
      .failsBuild()
      .trimmedOutputContains("> Task :straitjacketCheckLibs FAILED")
      .trimmedOutputContains(
        """
        com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)
        """
          .trimIndent()
      )
  }
}
