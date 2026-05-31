package straitjacket

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.SetProperty
import straitjacket.internal.applyRestriction
import straitjacket.internal.registerAggregateCheckTask
import straitjacket.internal.registerPerCatalogCheckTask

/** Sets up and configures the check task. */
public class StraitjacketPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      val extension = extensions.create("straitjacket", StraitjacketExtension::class.java)
      val enabled = extension.enabled.convention(true)
      val ignoredConfigurations = extension.ignoredConfigurations.convention(emptySet())
      val ignoredCatalogs = extension.ignoredCatalogs.convention(emptySet())

      val versionCatalogs = extensions.getByType(VersionCatalogsExtension::class.java)
      val matchingConfigs = configurations.matching { c ->
        c.shouldBeConstrained(ignoredConfigurations)
      }

      val aggregateCheck = registerAggregateCheckTask()

      pluginManager.withPlugin("base") {
        tasks.named("check").configure { t -> t.dependsOn(aggregateCheck) }
      }

      // Register resolution strategy and check task for every available catalog.
      // Ignored catalogs are skipped at execution time via the enabled/onlyIf checks.
      versionCatalogs.catalogNames.forEach { catalogName ->
        val versionCatalog = versionCatalogs.named(catalogName)
        val isIgnored = ignoredCatalogs.map { ignored -> catalogName in ignored }

        matchingConfigs.configureEach { configuration ->
          configuration.resolutionStrategy.eachDependency { details ->
            if (enabled.get() && !isIgnored.get()) {
              details.applyRestriction(versionCatalog)
            }
          }
        }

        val perCatalogCheck =
          registerPerCatalogCheckTask(
            catalogName = catalogName,
            versionCatalog = provider { versionCatalog },
            matchingConfigs = matchingConfigs,
            isIgnored = isIgnored,
          )
        aggregateCheck.configure { it.dependsOn(perCatalogCheck) }
      }
    }

  private fun Configuration.shouldBeConstrained(
    ignoredConfigurations: SetProperty<String>
  ): Boolean = isCanBeResolved && name !in ignoredConfigurations.get()
}
