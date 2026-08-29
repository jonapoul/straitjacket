package straitjacket

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.taskSucceeded
import blueprint.test.trimmedOutputContains
import blueprint.test.withArgument
import blueprint.test.withGradleProperty
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * okio resolves above what the catalog declares, so there is always something to report. Whether
 * that fails the build or is only logged depends on [StraitjacketExtension.failOnViolation], which
 * the `fail` Gradle property sets.
 */
class FailOnViolationScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      straitjacket {
        val fail = providers.gradleProperty("fail").map { it.toBoolean() }.orNull
        if (fail != null) {
          failOnViolation = fail
        }
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.16.4")
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
  fun `a violation fails the build by default`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains("com.squareup.okio:okio:3.16.0 -> 3.16.4")
  }

  @Test
  fun `a violation only warns when failOnViolation is off`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "fail", value = false)
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      .trimmedOutputContains(
        "Straitjacket found dependencies resolved to versions newer than the version catalog declares:",
        "com.squareup.okio:okio:3.16.0 -> 3.16.4",
        "Not failing the build because failOnViolation is off.",
      )
  }

  /**
   * A task that succeeds is up to date on the next build and does not log its warning again, so the
   * report file is what has to hold the record.
   */
  @Test
  fun `the report file records the violation when only warning`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "fail", value = false)
      .buildsSuccessfully()

    val report = rootDir.resolve("build/reports/straitjacket/libs.txt")
    assertThat(report.readText(), name = "report")
      .contains("com.squareup.okio:okio:3.16.0 -> 3.16.4")
  }

  @Test
  fun `the warning names the report file`() = runScenario {
    val report = rootDir.resolve("build/reports/straitjacket/libs.txt")
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "fail", value = false)
      .buildsSuccessfully()
      .trimmedOutputContains("See the Straitjacket report at ${report.absolutePath}")
  }

  /** Warning mode must stay quiet when there is nothing to report. */
  @Test
  fun `a passing check writes an empty report and warns about nothing`() = runScenario {
    // Bring the catalog up to the version okio resolves to, so nothing is above it. Declaring a
    // version above it instead would have the forcing side chase a version that does not exist.
    rootDir
      .resolve("gradle/libs.versions.toml")
      .writeText(
        """
        [libraries]
        okio = { module = "com.squareup.okio:okio", version = "3.16.4" }
        """
          .trimIndent()
      )

    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "fail", value = false)
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      .outputDoesNotContain("Not failing the build because failOnViolation is off.")

    val report = rootDir.resolve("build/reports/straitjacket/libs.txt")
    assertThat(report.readText(), name = "report").isEmpty()
  }

  @Test
  fun `the Gradle property makes a warning fail the build`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "fail", value = false)
      .withGradleProperty("straitjacket.failOnViolation", true)
      .failsBuild()
      .trimmedOutputContains("com.squareup.okio:okio:3.16.0 -> 3.16.4")
  }

  @Test
  fun `the Gradle property stops a failure at a warning`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "fail", value = true)
      .withGradleProperty("straitjacket.failOnViolation", false)
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
      .trimmedOutputContains("Not failing the build because failOnViolation is off.")
  }

  /**
   * The extension says not to fail, so a value that fell back would leave every one of these at a
   * warning and hide the typo. Nothing about the task's inputs changes between iterations, so
   * without `--rerun-tasks` the task would be up to date from the second one on and the assertion
   * would pass without running anything.
   */
  @Test
  fun `a non-boolean string for the property fails the build`() = runScenario {
    listOf("", "not-a-bool", "1", "TRUE", " false").forEach { property ->
      assertThatTask(":straitjacketCheck")
        .withGradleProperty(name = "fail", value = false)
        .withArgument("--rerun-tasks")
        .withGradleProperty(name = "straitjacket.failOnViolation", value = property)
        .failsBuild()
        .trimmedOutputContains(
          "Gradle property 'straitjacket.failOnViolation' is set to '$property', " +
            "which is not a boolean (true or false)."
        )
    }
  }
}
