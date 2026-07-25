package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.plusArguments
import straitjacket.test.settingsGradleKts
import straitjacket.test.trimmedOutputContains
import straitjacket.test.withGradleProperty

/**
 * okio is requested below its catalog version and so is forced up, and okhttp is requested at
 * exactly its catalog version and so is left alone. Whether the force is logged, and how loudly,
 * depends on [StraitjacketExtension.logForcedVersions], which the `level` Gradle property sets.
 *
 * `straitjacketCheck` resolves every resolvable configuration in the project, so it is enough to
 * make the forcing happen.
 */
class LogForcedVersionsScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      import org.gradle.api.logging.LogLevel

      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      straitjacket {
        val level = providers.gradleProperty("level").orNull
        if (level != null) {
          logForcedVersions = LogLevel.valueOf(level)
        }
        // Stands in for a leaf project opting out of a level a convention plugin set
        if (providers.gradleProperty("unset").isPresent) {
          logForcedVersions.unset()
        }
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.6.0")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      okhttp = { module = "com.squareup.okhttp3:okhttp", version = "4.12.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `forcing is silent when no level is set`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .outputDoesNotContain("Straitjacket forced")
  }

  @Test
  fun `a forced dependency is logged at the level set`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=LIFECYCLE")
      .buildsSuccessfully()
      .outputContains(
        "Straitjacket forced com.squareup.okio:okio 3.6.0 -> 3.16.0 in runtimeClasspath (catalog 'libs')"
      )
  }

  /**
   * The reason for taking a level rather than a boolean: INFO keeps it out of a normal build and
   * behind `--info`, so it can be left on while working something out.
   */
  @Test
  fun `a level below lifecycle stays out of a normal build`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=INFO")
      .buildsSuccessfully()
      .outputDoesNotContain("Straitjacket forced")
  }

  @Test
  fun `a level below lifecycle shows when the build asks for it`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=INFO")
      .plusArguments("--info")
      .buildsSuccessfully()
      .outputContains("Straitjacket forced com.squareup.okio:okio 3.6.0 -> 3.16.0")
  }

  /** Every configuration that resolved it says so, rather than the first one to get there. */
  @Test
  fun `a force is logged against each configuration that resolved it`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=LIFECYCLE")
      .buildsSuccessfully()
      .outputContains(
        "Straitjacket forced com.squareup.okio:okio 3.6.0 -> 3.16.0 in compileClasspath (catalog 'libs')"
      )
      .outputContains(
        "Straitjacket forced com.squareup.okio:okio 3.6.0 -> 3.16.0 in testRuntimeClasspath (catalog 'libs')"
      )
  }

  /** Only an actual force is worth a line, not every dependency the catalog happens to declare. */
  @Test
  fun `a dependency already at its catalog version is not logged`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=LIFECYCLE")
      .buildsSuccessfully()
      .outputContains("Straitjacket forced com.squareup.okio:okio")
      .outputDoesNotContain("Straitjacket forced com.squareup.okhttp3:okhttp")
  }

  /** Backs the claim in the KDoc that this is how you opt out of an inherited level. */
  @Test
  fun `unset silences a level already set`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=LIFECYCLE", "-Punset=true")
      .buildsSuccessfully()
      .outputDoesNotContain("Straitjacket forced")
  }

  @Test
  fun `the Gradle property sets a level over the extension`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "straitjacket.logForcedVersions", value = "LIFECYCLE")
      .buildsSuccessfully()
      .outputContains("Straitjacket forced com.squareup.okio:okio 3.6.0 -> 3.16.0")
  }

  @Test
  fun `the Gradle property quietens a level the extension set`() = runScenario {
    assertThatTask(":straitjacketCheck", "-Plevel=LIFECYCLE")
      .withGradleProperty(name = "straitjacket.logForcedVersions", value = "INFO")
      .buildsSuccessfully()
      .outputDoesNotContain("Straitjacket forced")
  }

  @Test
  fun `the Gradle property is matched to a level without regard to case`() = runScenario {
    listOf("lifecycle", "LiFeCyClE").forEach { property ->
      assertThatTask(":straitjacketCheck", "--rerun-tasks")
        .withGradleProperty(name = "straitjacket.logForcedVersions", value = property)
        .buildsSuccessfully()
        .outputContains("Straitjacket forced com.squareup.okio:okio 3.6.0 -> 3.16.0")
    }
  }

  /**
   * Falling back would leave the extension's level in place and hide the typo. The level is only
   * read while forcing, so this needs a task that resolves a configuration: `compileKotlin` does,
   * where `straitjacketCheck` only walks the resolution result.
   */
  @Test
  fun `a property that names no level fails the build`() = runScenario {
    listOf("", "not-a-level", "true", "verbose").forEach { property ->
      assertThatTask(":compileKotlin", "-Plevel=LIFECYCLE", "--rerun-tasks")
        .withGradleProperty(name = "straitjacket.logForcedVersions", value = property)
        .failsBuild()
        .trimmedOutputContains(
          "Gradle property 'straitjacket.logForcedVersions' is set to '$property', " +
            "which is not a log level (DEBUG, INFO, LIFECYCLE, WARN, QUIET, ERROR)."
        )
    }
  }
}
