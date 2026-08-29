package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.tasksSucceeded
import blueprint.test.trimmedOutputContains
import blueprint.test.withGradleProperty
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * Two catalogs can declare the same module at different versions. Straitjacket registers a
 * resolution rule and a check task per catalog, so both sides have to agree on which declared
 * version wins, and the answer must not depend on the order the catalogs were registered in. The
 * cross-catalog twin of [DuplicateAliasScenario], settled the same way: the highest wins.
 *
 * Registration order used to decide it instead, breaking in opposite directions:
 * - Later catalog declares higher: it won the forcing, then the earlier catalog's check failed the
 *   build against its own lower declaration, telling you to update a catalog to a version another
 *   catalog already declared.
 * - Later catalog declares lower: it pulled the version back down and every check passed silently,
 *   since a check only reports resolved versions above its catalog and never below.
 *
 * `libs` declares 3.6.0; `someOtherLibs` is registered afterwards and takes its version from the
 * `otherOkioVersion` property, so one fixture covers both orderings. The requested version defaults
 * below both, so the forcing side is guaranteed to act.
 *
 * The `printResolvedOkio` tests stop the check tests being satisfied by forcing to the lowest
 * declared version, which would turn them green while making the plugin ignore the higher pin.
 */
class CrossCatalogDuplicateScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        // Registered after the default 'libs' catalog, so this is the last catalog Straitjacket
        // registers a resolution rule for. Declared in code rather than a TOML so that the version
        // can vary per test, which is what lets one fixture cover both registration orderings.
        dependencyResolutionManagement {
          versionCatalogs {
            create("someOtherLibs") {
              library("okio", "com.squareup.okio", "okio")
                .version(providers.gradleProperty("otherOkioVersion").getOrElse("3.16.0"))
            }
          }
        }
      """
        .trimIndent()
    )

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      // Defaults below both declared versions, so the forcing side is guaranteed to kick in.
      dependencies {
        implementation("com.squareup.okio:okio:" + providers.gradleProperty("okioVersion").getOrElse("3.0.0"))
      }

      // Resolving a configuration in a doLast block is not configuration-cache compatible, so the
      // tests that use this task opt out of the cache the harness enables by default.
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
      okio = { module = "com.squareup.okio:okio", version = "3.6.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `the highest version wins when the catalog registered last declares it`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withGradleProperty(name = "otherOkioVersion", value = "3.16.0")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.16.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.6.0")
  }

  @Test
  fun `the highest version wins when the catalog registered first declares it`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withGradleProperty(name = "otherOkioVersion", value = "3.1.0")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.6.0")
      .outputDoesNotContain("RESOLVED_OKIO=3.1.0")
  }

  @Test
  fun `every catalog passes the check when the catalog registered last declares the highest`() =
    runScenario {
      assertThatTask(":straitjacketCheck")
        .withGradleProperty(name = "otherOkioVersion", value = "3.16.0")
        .buildsSuccessfully()
        .tasksSucceeded(":straitjacketCheckLibs", ":straitjacketCheckSomeOtherLibs")
    }

  @Test
  fun `every catalog passes the check when the catalog registered first declares the highest`() =
    runScenario {
      assertThatTask(":straitjacketCheck")
        .withGradleProperty(name = "otherOkioVersion", value = "3.1.0")
        .buildsSuccessfully()
        .tasksSucceeded(":straitjacketCheckLibs", ":straitjacketCheckSomeOtherLibs")
    }

  // 3.16.4 is above every version either catalog declares, so nothing is forced and the violation
  // is genuine. `libs` is asked for by name rather than through the aggregate task, because
  // `someOtherLibs` fails too and only the first failing task in the graph would get to run.
  //
  // The version reported is the one the forcing side aimed for, 3.16.0, not `libs`' own 3.6.0.
  // Reporting 3.6.0 would be telling you to update a catalog to a version it is already being held
  // above by another catalog. That means a per-catalog check can no longer be read purely in terms
  // of its own catalog, so the message names where the version came from.
  @Test
  fun `a violation is reported against the highest version any catalog declares`() = runScenario {
    assertThatTask(":straitjacketCheckLibs")
      .withGradleProperty(name = "otherOkioVersion", value = "3.16.0")
      .withGradleProperty(name = "okioVersion", value = "3.16.4")
      .failsBuild()
      .trimmedOutputContains("> Task :straitjacketCheckLibs FAILED")
      .trimmedOutputContains(
        """
        com.squareup.okio:okio:3.16.0 (declared by catalog 'someOtherLibs') -> 3.16.4 (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)
        """
          .trimIndent()
      )
  }
}
