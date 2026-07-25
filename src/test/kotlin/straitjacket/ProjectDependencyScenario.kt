package straitjacket

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.assertThatTask
import blueprint.test.buildsSuccessfully
import blueprint.test.outputContains
import blueprint.test.outputDoesNotContain
import kotlin.test.Test
import straitjacket.test.StraitjacketScenarioTest
import straitjacket.test.buildGradleKts
import straitjacket.test.libsVersionsToml
import straitjacket.test.settingsGradleKts
import straitjacket.test.withoutConfigurationCache

// A repo that publishes its own modules commonly declares them in its own catalog, and the catalog
// entry runs ahead of the version the project currently builds at. The catalog then declares a
// version higher than the project dependency resolves to, and forcing must leave the project
// dependency alone: useVersion on a project component substitutes it for an external module that
// only exists once published.
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

      // Resolves runtimeClasspath and prints what :sub resolved to, so the test asserts the project
      // dependency survived resolution rather than only that the build did not blow up. The files
      // are resolved first because a component that failed to resolve is simply absent from
      // allComponents, so walking the graph alone would report a substituted-away project
      // dependency as a silent nothing.
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
      // Resolving a configuration in a doLast block is not configuration-cache compatible, so this
      // fixture task opts out of the cache that the harness enables by default.
      assertThatTask(":printResolvedSub")
        .withoutConfigurationCache()
        .buildsSuccessfully()
        .outputContains("RESOLVED_SUB=project :sub")
        .outputDoesNotContain("com.example:sub:3.0.0")
    }
}
