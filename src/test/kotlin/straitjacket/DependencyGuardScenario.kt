package straitjacket

import assertk.assertThat
import assertk.assertions.doesNotContain
import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.settingsGradleKts
import blueprint.test.taskFailed
import blueprint.test.taskSucceeded
import blueprint.test.tasksSucceeded
import blueprint.test.trimmedOutputContains
import blueprint.test.withArgument
import blueprint.test.withGradleProperty
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.assertThatTaskWithConfigurationCache
import straitjacket.test.contains

/**
 * Dependency Guard records the versions a configuration resolves to, so the two plugins look at the
 * same thing from opposite ends: Straitjacket decides what resolution produces, Dependency Guard
 * writes down what came out. The baseline holding the forced version is what shows they agree,
 * rather than Dependency Guard recording the version the build file asked for.
 *
 * The work is in a subproject because Dependency Guard refuses to guard anything but the
 * buildscript `classpath` configuration on a root project.
 */
class DependencyGuardScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        include(":lib")
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

    "lib" {
      buildGradleKts(
        """
        plugins {
          kotlin("jvm")
          id("dev.jonpoulton.straitjacket")
          id("com.dropbox.dependency-guard")
        }

        dependencyGuard {
          configuration("runtimeClasspath")
        }

        dependencies {
          implementation("com.squareup.okio:okio:3.6.0")
        }
        """
          .trimIndent()
      )
    }
  }

  @Test
  fun `the baseline records the version straitjacket forced`() = runScenario {
    assertThatTaskWithConfigurationCache(":lib:dependencyGuardBaseline").buildsSuccessfully()

    val baseline = rootDir.resolve("lib/dependencies/runtimeClasspath.txt").readText()
    assertThat(baseline, name = "baseline")
      .contains("com.squareup.okio:okio:3.16.0")
      .doesNotContain("com.squareup.okio:okio:3.6.0")
  }

  @Test
  fun `both checks pass in the same build`() = runScenario {
    assertThatTaskWithConfigurationCache(":lib:dependencyGuardBaseline").buildsSuccessfully()

    assertThatTaskWithConfigurationCache(":lib:check")
      .buildsSuccessfully()
      .tasksSucceeded(
        ":lib:dependencyGuard",
        ":lib:straitjacketCheck",
        ":lib:straitjacketCheckLibs",
      )
  }

  /**
   * What a repo adopting Straitjacket sees on its first build. Disabling the plugin produces the
   * baseline the repo already had, so both files here come out of a real build, and turning the
   * plugin back on has to report the bump as the diff it is rather than the two plugins quietly
   * disagreeing about what the classpath holds.
   */
  @Test
  fun `a baseline recorded with straitjacket disabled fails the dependency guard check`() =
    runScenario {
      assertThatTaskWithConfigurationCache(":lib:dependencyGuardBaseline")
        .withGradleProperty("straitjacket.enabled", false)
        .buildsSuccessfully()

      assertThatTaskWithConfigurationCache(":lib:dependencyGuard")
        .withGradleProperty("straitjacket.enabled", true)
        .failsBuild()
        .trimmedOutputContains("- com.squareup.okio:okio:3.6.0", "+ com.squareup.okio:okio:3.16.0")
    }

  /**
   * A violation is the one thing Straitjacket cannot fix by forcing, so it has to fail the build
   * with Dependency Guard applied just as it does without it. The baseline keeps matching
   * throughout: it records what resolution produced, and a violation is resolution doing exactly
   * what it was asked.
   */
  @Test
  fun `a violation fails the check while the dependency guard baseline still matches`() =
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

      assertThatTaskWithConfigurationCache(":lib:dependencyGuardBaseline").buildsSuccessfully()

      // Both tasks hang off check, and without --continue the build stops at the first one to
      // fail, so whether Dependency Guard ran at all would come down to task ordering.
      assertThatTaskWithConfigurationCache(":lib:check")
        .withArgument("--continue")
        .failsBuild()
        .taskFailed(":lib:straitjacketCheckLibs")
        .taskSucceeded(":lib:dependencyGuard")
        .trimmedOutputContains("com.squareup.okio:okio:3.5.0 -> 3.6.0")
    }
}
