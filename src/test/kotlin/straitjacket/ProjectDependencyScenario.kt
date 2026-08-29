package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildGradleKts
import blueprint.test.buildsSuccessfully
import blueprint.test.libsVersionsToml
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import blueprint.test.settingsGradleKts
import blueprint.test.withoutConfigurationCache
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest

// A repo that publishes its own modules declares them in its own catalog, and that entry runs ahead
// of the version the project builds at. Forcing it swaps in a module that only exists once
// published.
class ProjectDependencyScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts(
      """
        $DEFAULT_REPOSITORIES_KTS

        include(":sub")
      """
        .trimIndent()
    )

    buildGradleKts(
      """
      plugins {
        kotlin("jvm")
        id("dev.jonpoulton.straitjacket")
      }

      dependencies {
        implementation(project(":sub"))
      }

      // The files are resolved first because a component that failed to resolve is simply absent
      // from allComponents, so walking the graph alone reports the bug as a silent nothing.
      tasks.register("printResolvedSub") {
        doLast {
          val runtimeClasspath = configurations.getByName("runtimeClasspath")
          runtimeClasspath.files
          runtimeClasspath.incoming.resolutionResult.allComponents {
            logger.lifecycle("RESOLVED_SUB=" + id.displayName)
          }
        }
      }
      """
        .trimIndent()
    )

    libsVersionsToml(
      """
      [libraries]
      sub = { module = "com.example:sub", version = "3.0.0" }
      """
        .trimIndent()
    )

    "sub" {
      buildGradleKts(
        """
        plugins {
          kotlin("jvm")
        }

        group = "com.example"
        version = "2.0.0"
        """
          .trimIndent()
      )
    }
  }

  @Test
  fun `a project dependency below the catalog version is not forced into an external module`() =
    runScenario {
      // Resolving in doLast is not configuration-cache compatible, so this opts out of the cache
      // the harness enables by default.
      assertThatTask(":printResolvedSub")
        .withoutConfigurationCache()
        .buildsSuccessfully()
        .outputContains("RESOLVED_SUB=project ':sub'")
        .outputDoesNotContain("com.example:sub:3.0.0")
    }
}
