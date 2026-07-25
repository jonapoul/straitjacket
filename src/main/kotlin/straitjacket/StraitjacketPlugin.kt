package straitjacket

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.SetProperty
import straitjacket.internal.applyRestriction
import straitjacket.internal.buildAuthoritativeVersionMap
import straitjacket.internal.buildCatalogVersionMap
import straitjacket.internal.registerAggregateCheckTask
import straitjacket.internal.registerPerCatalogCheckTask

public class StraitjacketPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      val extension = extensions.create("straitjacket", StraitjacketExtension::class.java)

      // Finalized on read because the rules below ask for this once per dependency, per catalog,
      // for every resolution in the build, and Gradle does not memoise a provider chain. Nothing
      // can change the answer by then: resolution happens long after the extension is configured
      val enabled =
        objects
          .property(Boolean::class.java)
          .value(
            providers
              .gradleProperty("straitjacket.enabled")
              .map(String::toBooleanStrictOrNull)
              .orElse(extension.enabled)
              .orElse(true)
          )
          .apply { finalizeValueOnRead() }

      val ignoredConfigurations = extension.ignoredConfigurations.convention(emptySet())
      val ignoredCatalogs = extension.ignoredCatalogs.convention(emptySet())

      val versionCatalogs = extensions.getByType(VersionCatalogsExtension::class.java)
      val matchingConfigs = configurations.matching { c ->
        c.shouldBeConstrained(ignoredConfigurations)
      }

      val aggregateCheck = registerAggregateCheckTask(enabled)

      pluginManager.withPlugin("base") {
        tasks.named("check").configure { t -> t.dependsOn(aggregateCheck) }
      }

      // Built once per catalog so that forcing and checking can never disagree about what a catalog
      // declares. See buildCatalogVersionMap for how duplicate aliases are resolved.
      val versionsByCatalog =
        versionCatalogs.catalogNames.associateWith { catalogName ->
          buildCatalogVersionMap(versionCatalogs.named(catalogName))
        }

      // Which catalog wins a module declared by several of them. Stays a provider because
      // ignoredCatalogs is only final once the build script has configured the extension, and an
      // ignored catalog must not contribute a version.
      val authoritativeVersions = ignoredCatalogs.map { ignored ->
        buildAuthoritativeVersionMap(versionsByCatalog.filterKeys { it !in ignored })
      }

      // Register resolution strategy and check task for every available catalog.
      versionsByCatalog.forEach { (catalogName, catalogVersions) ->
        // Finalized on read for the same reason as enabled, which it builds on
        val active =
          objects
            .property(Boolean::class.java)
            .value(
              enabled.zip(ignoredCatalogs) { isEnabled, ignored ->
                isEnabled && catalogName !in ignored
              }
            )
            .apply { finalizeValueOnRead() }

        matchingConfigs.configureEach { configuration ->
          configuration.resolutionStrategy.eachDependency { details ->
            if (active.get()) {
              details.applyRestriction(catalogName, catalogVersions)
            }
          }
        }

        val perCatalogCheck =
          registerPerCatalogCheckTask(
            catalogName = catalogName,
            catalogVersions = catalogVersions,
            authoritativeVersions = authoritativeVersions,
            matchingConfigs = matchingConfigs,
            active = active,
          )
        aggregateCheck.configure { it.dependsOn(perCatalogCheck) }
      }
    }

  private fun Configuration.shouldBeConstrained(
    ignoredConfigurations: SetProperty<String>
  ): Boolean = isCanBeResolved && name !in ignoredConfigurations.get()
}
