package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.settingsGradleKts
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

/**
 * Every per-catalog check task judges the same resolved graph, so the graph should only be walked
 * once however many catalogs the build declares. It used to be walked per task, which meant every
 * resolvable configuration in the project was traversed N times for N catalogs.
 *
 * There is no version catalog plugin in this fixture, and no Kotlin or Java plugin either, so
 * `custom` is the project's only resolvable configuration. That makes the count of filter-spec
 * evaluations at execution time exactly the count of map builds.
 */
class ResolvedVersionMapOverheadScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
      $DEFAULT_REPOSITORIES_KTS

      dependencyResolutionManagement {
        versionCatalogs {
          create("moreLibs") { from(files("gradle/moreLibs.versions.toml")) }
          create("evenMoreLibs") { from(files("gradle/evenMoreLibs.versions.toml")) }
        }
      }
      """
        .trimIndent()
    )

    buildGradleKts(
      """
      import java.util.concurrent.atomic.AtomicInteger

      plugins {
        id("dev.jonpoulton.straitjacket")
      }

      // Building the map iterates the filtered configuration container, and the filter spec reading
      // ignoredConfigurations is the only part of that a build script can get a hook into.
      val mapBuilds = AtomicInteger()

      straitjacket {
        ignoredConfigurations.set(providers.provider { mapBuilds.incrementAndGet(); emptySet() })
      }

      configurations.create("custom") { isCanBeResolved = true }

      dependencies {
        "custom"("com.squareup.okio:okio:3.16.0")
      }

      // The spec is evaluated at configuration time as well, and only the execution-time
      // evaluations are the point here.
      val resetCount = tasks.register("resetCount") { doLast { mapBuilds.set(0) } }
      tasks.withType<straitjacket.StraitjacketCheck>().configureEach { dependsOn(resetCount) }

      // The counter is mutable state shared between a provider and a task action, so this test
      // opts out of the configuration cache the harness enables by default.
      tasks.register("countMapBuilds") {
        dependsOn("straitjacketCheck")
        doLast {
          logger.lifecycle("MAP_BUILDS=" + mapBuilds.get())
        }
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

    ("gradle" / "moreLibs.versions.toml")(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )

    ("gradle" / "evenMoreLibs.versions.toml")(
      """
      [libraries]
      okio = { module = "com.squareup.okio:okio", version = "3.16.0" }
      """
        .trimIndent()
    )
  }

  @Test
  fun `the resolved version map is built once however many catalogs there are`() = runScenario {
    assertThatTask(":countMapBuilds")
      .withoutConfigurationCache()
      .buildsSuccessfully()
      .outputContains("MAP_BUILDS=1")
  }
}
