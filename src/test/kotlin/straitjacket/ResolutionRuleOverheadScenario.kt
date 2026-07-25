package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.outputContains
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.withoutConfigurationCache

/**
 * The resolution rule asks whether Straitjacket is active for every dependency of every resolution,
 * so the answer has to be computed once and cached. It used to be a plain `zip`, which Gradle does
 * not memoise, and this fixture counted 164 evaluations instead of one.
 *
 * The work runs through `straitjacketCheck` because that resolves every resolvable configuration in
 * the project, so caching per resolution rather than once would still show a count above one.
 */
class ResolutionRuleOverheadScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      import java.util.concurrent.atomic.AtomicInteger

      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      // A provider is the only part of the chain a build script can get a hook into.
      val evalCount = AtomicInteger()

      straitjacket {
        enabled = providers.provider { evalCount.incrementAndGet(); true }
      }

      // Every transitive here sits below what the catalog declares, so all of them get forced up
      // and the check passes.
      dependencies {
        implementation(libs.retrofit)
        implementation(libs.okhttp)
        implementation(libs.okio)
      }

      // The counter is mutable state shared between a provider and a task action, so this test
      // opts out of the configuration cache the harness enables by default.
      tasks.register("countEvaluations") {
        dependsOn("straitjacketCheck")
        doLast {
          logger.lifecycle("EVAL_COUNT=" + evalCount.get())
        }
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      retrofit = { module = "com.squareup.retrofit2:retrofit", version = "2.11.0" }
      okhttp = { module = "com.squareup.okhttp3:okhttp", version = "4.12.0" }
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `the enabled provider is evaluated once however many configurations resolve`() = runScenario {
    assertThatTask(":countEvaluations")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("EVAL_COUNT=1")
  }
}
