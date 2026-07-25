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
import straitjacket.internal.resolvedVersions

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
      enabled.finalizeValueOnRead()

      val ignoredConfigurations = extension.ignoredConfigurations.convention(emptySet())
      ignoredConfigurations.finalizeValueOnRead()

      val versionCatalogs = extensions.getByType(VersionCatalogsExtension::class.java)

      val resolvableConfigs = configurations.matching(Configuration::isCanBeResolved)
      val checkedConfigs = configurations.matching { c -> c.shouldBeChecked(ignoredConfigurations) }

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

      // Shared by every per-catalog check task, so the resolution graph is only walked once
      val resolvedVersions = resolvedVersions(checkedConfigs)

      // Which catalog wins a module declared by several of them. Stays a provider because
      // ignoredCatalogs is only final once the build script has configured the extension, and an
      // ignored catalog must not contribute a version.
      val ignoredCatalogs = extension.ignoredCatalogs.convention(emptySet())
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
        active.finalizeValueOnRead()

        resolvableConfigs.configureEach { configuration ->
          // The name, not the Configuration, so the rule stays configuration cache friendly
          val configurationName = configuration.name
          // A substitution rule rather than eachDependency, because it is handed the selector
          // itself. eachDependency only reports coordinates, and a project dependency reports the
          // target project's own group, name and version, so there is no way to tell one from a
          // module and forcing it substitutes the project for an unpublished module.
          configuration.resolutionStrategy.dependencySubstitution.all { substitution ->
            if (active.get() && configurationName !in ignoredConfigurations.get()) {
              substitution.applyRestriction(catalogName, catalogVersions)
            }
          }
        }

        val perCatalogCheck =
          registerPerCatalogCheckTask(
            catalogName = catalogName,
            catalogVersions = catalogVersions,
            authoritativeVersions = authoritativeVersions,
            resolvedVersions = resolvedVersions,
            active = active,
          )
        aggregateCheck.configure { it.dependsOn(perCatalogCheck) }
      }
    }

  private fun Configuration.shouldBeChecked(ignoredConfigurations: SetProperty<String>): Boolean =
    isCanBeResolved && name !in ignoredConfigurations.get()
}
