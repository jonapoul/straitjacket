package straitjacket

import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.settingsGradleKts
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * A configuration created while another one resolves must not break the check.
 *
 * `resolvedVersions` in `internal/tasks.kt` resolves each configuration from inside a walk of
 * `configurations.matching { ... }`, a live view of the project's configuration container. Without
 * the snapshot it takes, anything adding a configuration during one of those resolutions
 * invalidates the iterator and the walk dies with a `ConcurrentModificationException` from
 * `FilteredElementSource$FilteringIterator.findNext`.
 *
 * `compileClasspath` is the earliest resolvable configuration the walk reaches, so the iterator
 * still has elements left when the container changes underneath it. The `beforeResolve` hook stands
 * in for AGP, which creates `androidApis` that way;
 * [AndroidConfigurationCreatedDuringResolutionScenario] is the real-AGP twin.
 *
 * The configuration cache is not the cause, only where it surfaces: `resolvedVersions` is an
 * `@Input`, so the walk runs while the task graph is stored. It breaks the same way at execution
 * time without it. `ConfigCacheScenario` stays green because a plain `kotlin("jvm")` project never
 * adds a configuration during resolution.
 */
class ConfigurationCreatedDuringResolutionScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      // Stands in for AGP creating androidApis while the unit test compile classpath resolves
      configurations.named("compileClasspath") {
        incoming.beforeResolve {
          if (configurations.findByName("lateConfiguration") == null) {
            configurations.dependencyScope("lateConfiguration").get()
          }
        }
      }

      dependencies {
        implementation("com.squareup.okio:okio:3.6.0")
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
  fun `a configuration created during resolution does not break the check`() = runScenario {
    assertThatTask(":straitjacketCheck")
      .buildsSuccessfully()
      .outputContains("Configuration cache entry stored.")
  }
}
