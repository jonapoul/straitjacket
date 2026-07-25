package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.outputContains
import blueprint.test.taskSucceeded
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.withoutConfigurationCache

/**
 * FAILING TEST - documents a known bug, not yet fixed.
 *
 * ## The bug
 *
 * When two version catalogs declare the same module at different versions, Straitjacket forces the
 * version declared by whichever catalog was registered *last*, but every catalog still checks the
 * resolved version against its own declaration. The two sides therefore disagree, and the build can
 * fail on the exact version Straitjacket itself forced.
 *
 * This is the cross-catalog twin of the duplicate-alias bug (a module declared under two aliases
 * *within* one catalog). Fixing that one does not fix this one - see "Relationship to the other
 * known bugs" below.
 *
 * ## Where
 *
 * `StraitjacketPlugin.apply` loops over `versionCatalogs.catalogNames` and, for each catalog
 * independently, registers both
 * - a `resolutionStrategy.eachDependency` rule that calls `applyRestriction` for that catalog, and
 * - a `straitjacketCheck<CatalogName>` task that checks against that catalog.
 *
 * Nothing reconciles the catalogs with each other. `applyRestriction` in `internal/restrictions.kt`
 * compares `requested.version` against its own catalog's version, and `requested` always reports
 * the version the build originally asked for - never the value a previously-registered catalog's
 * rule already set via `useVersion`. So each catalog's rule decides in isolation from the same
 * starting point, and the last rule to run is the one whose version sticks. It can therefore pull
 * the version back *down* relative to what an earlier catalog's rule chose (though never below the
 * version the build originally requested).
 *
 * Meanwhile `buildCatalogVersionMap` and `StraitjacketCheck.execute` give every catalog its own
 * check task, each comparing the single resolved version against its own declaration. Any catalog
 * declaring something lower than the version that won the forcing reports a violation.
 *
 * ## This scenario
 *
 * Two catalogs declare okio at different versions, and the build requests 3.0.0, which is below
 * both, so the forcing side is guaranteed to act:
 * - `libs` (from `gradle/libs.versions.toml`) declares 3.6.0
 * - `someOtherLibs` (registered afterwards in `settings.gradle.kts`) declares 3.16.0
 *
 * `someOtherLibs` is registered last, so 3.16.0 wins the forcing. `:straitjacketCheckSomeOtherLibs`
 * is happy with that. `:straitjacketCheckLibs` is not: it compares the resolved 3.16.0 against
 * `libs`' 3.6.0, finds resolved is newer, and fails the build.
 *
 * ## Expected vs actual
 *
 * `the check passes ...` is the failing test. Both check tasks should succeed: 3.16.0 is declared
 * by a catalog in this build, so no catalog should be reporting it as a violation. What actually
 * happens today is:
 * ```
 * > Task :straitjacketCheckSomeOtherLibs
 * > Task :straitjacketCheckLibs FAILED
 *
 * > Straitjacket found dependencies resolved to versions newer than the version catalog declares:
 *
 *     com.squareup.okio:okio:3.6.0 -> 3.16.0
 *     (in compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)
 *
 *   Update your version catalog or add these configurations to ignoredConfigurations.
 * ```
 *
 * (the violation is a single line in the real output, wrapped here to fit the line length limit)
 *
 * telling you to update a catalog to a version that another catalog in the same build already
 * declares, for a bump Straitjacket performed itself.
 *
 * `the module is forced up ...` passes today, and is here as a trap guard: it pins down that the
 * *forcing* side did the sensible thing in this ordering, so the failure above is not a reason to
 * start forcing to the lowest declared version. That would turn the failing test green while making
 * the plugin ignore the higher pin, which is the second symptom described next.
 *
 * ## The mirror ordering, which fails silently
 *
 * Swapping the two declarations - `libs` at 3.16.0 and `someOtherLibs` at 3.6.0 - was verified to
 * resolve okio to **3.6.0**, and both check tasks pass. That confirms the winner is decided by
 * registration order rather than by version, and it is arguably the worse of the two symptoms:
 * `libs` declares a 3.16.0 pin, the build quietly resolves below it, and no check ever notices
 * because `StraitjacketCheck.execute` only reports resolved versions *above* the catalog and never
 * below.
 *
 * That ordering is deliberately not the fixture here, because a single scenario cannot assert both
 * symptoms at once and this one is the loud, user-visible failure. Whoever fixes this should check
 * the mirror ordering by hand as well. A correct fix makes both orderings force 3.16.0 and pass, at
 * which point the distinction disappears and one fixture covers both.
 *
 * ## Suggested fix
 *
 * The forcing side needs a single authoritative version per module across *all* catalogs, and the
 * checking side has to agree with it. Two defensible shapes:
 * 1. Reconcile across catalogs: build one coordinate to version map from every non-ignored catalog,
 *    taking the highest declared version, and have both the single resolution rule and the check
 *    tasks read it. Consistent with how the plugin already forces upward, and it fixes both
 *    symptoms at once. It does mean a per-catalog check task can no longer be understood purely in
 *    terms of its own catalog, so the violation message should probably name the catalog that
 *    supplied the authoritative version.
 * 2. Keep catalogs independent but make each check judge only what its own catalog is authoritative
 *    for, so a catalog never reports a violation for a version another catalog declares. Preserves
 *    per-catalog isolation, but leaves the question of which catalog wins the forcing unanswered,
 *    and so leaves the silently-ignored-pin symptom in place.
 *
 * Option 1 is the more complete fix. Either way `ignoredCatalogs` has to be honoured when deciding
 * what is authoritative: an ignored catalog must not contribute a version.
 *
 * ## Relationship to the other known bugs
 *
 * Independent of both, despite the family resemblance:
 * - **Duplicate alias** (same module under two aliases in *one* catalog): fixed by resolving
 *   duplicates to the highest declared version in one shared place. That fix is per-catalog by
 *   construction, so this scenario still fails with it applied - verified.
 * - **Reported configurations** (`buildResolvedVersionMap` pairing the highest resolved version
 *   with every configuration the module appeared in): purely about the accuracy of an otherwise
 *   genuine violation report, and does not involve the forcing side at all. Note that the `(in
 *   ...)` list in the output above comes from that code path, so its wording may shift when that
 *   bug is fixed.
 */
class CrossCatalogDuplicateScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        // Registered after the default 'libs' catalog, which makes it the last one Straitjacket
        // registers a resolution rule for, and so the one that wins the forcing.
        dependencyResolutionManagement {
          versionCatalogs {
            create("someOtherLibs") {
              from(files("gradle/someOtherLibs.versions.toml"))
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

      // Below both declared versions, so the forcing side is guaranteed to kick in.
      dependencies {
        implementation("com.squareup.okio:okio:3.0.0")
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
      okio = { module = "com.squareup.okio:okio", version = "3.6.0" }
      """
        .trimIndent()
    )

    ("gradle" / "someOtherLibs.versions.toml")(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `the check passes when two catalogs declare different versions of the same module`() =
    runScenario {
      assertThatTask(":straitjacketCheck")
        .buildsSuccessfully()
        .taskSucceeded(":straitjacketCheckLibs")
        .taskSucceeded(":straitjacketCheckSomeOtherLibs")
    }

  @Test
  fun `the module is forced up to the highest version any catalog declares`() = runScenario {
    assertThatTask(":printResolvedOkio")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("RESOLVED_OKIO=3.16.0")
  }
}
