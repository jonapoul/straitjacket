package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.failsBuild
import blueprint.test.libsVersionsToml
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.taskSucceeded
import blueprint.test.trimmedOutputContains
import blueprint.test.withGradleProperty
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * Two custom configurations each resolve okio above what the catalog declares, and the `ignore`
 * Gradle property supplies the pattern added to [StraitjacketExtension.ignoredConfigurations].
 * Which of the two is left in the report says what the pattern covered.
 *
 * Names are picked to stand in for the generated per-variant configurations that motivate patterns
 * in the first place, where enumerating every name would mean keeping a list in sync with the
 * variants.
 */
class IgnoreConfigurationPatternScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      val debugUnitTestRuntime by configurations.creating { isCanBeResolved = true }
      val releaseUnitTestRuntime by configurations.creating { isCanBeResolved = true }

      straitjacket {
        val pattern = providers.gradleProperty("ignore").orNull
        if (pattern != null) {
          ignoredConfigurations.add(pattern)
        }
      }

      dependencies {
        debugUnitTestRuntime("com.squareup.okio:okio:3.16.4")
        releaseUnitTestRuntime("com.squareup.okio:okio:3.16.4")
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
  fun `both configurations are reported when nothing is ignored`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .failsBuild()
      .trimmedOutputContains("(in debugUnitTestRuntime, releaseUnitTestRuntime)")
  }

  /** The behaviour before patterns existed, which a name without a `*` has to keep. */
  @Test
  fun `an exact name still ignores only that configuration`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "ignore", value = "debugUnitTestRuntime")
      .failsBuild()
      .trimmedOutputContains("(in releaseUnitTestRuntime)")
      .outputDoesNotContain("debugUnitTestRuntime")
  }

  @Test
  fun `a leading wildcard ignores every matching configuration`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "ignore", value = "*UnitTestRuntime")
      .buildsSuccessfully()
      .taskSucceeded(":straitjacketCheckLibs")
  }

  @Test
  fun `a trailing wildcard ignores only the configurations it covers`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "ignore", value = "debug*")
      .failsBuild()
      .trimmedOutputContains("(in releaseUnitTestRuntime)")
      .outputDoesNotContain("debugUnitTestRuntime")
  }

  @Test
  fun `a pattern matching no configuration changes nothing`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .withGradleProperty(name = "ignore", value = "*AndroidTest*")
      .failsBuild()
      .trimmedOutputContains("(in debugUnitTestRuntime, releaseUnitTestRuntime)")
  }
}
