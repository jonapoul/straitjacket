package straitjacket

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import straitjacket.internal.StraitjacketSettings
import straitjacket.internal.buildAuthoritativeVersionMap
import straitjacket.internal.buildCatalogVersionMap
import straitjacket.internal.forceCatalogVersions
import straitjacket.internal.registerAggregateCheckTask
import straitjacket.internal.registerPerCatalogCheckTask
import straitjacket.internal.resolvedVersions

public class StraitjacketPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      val extension = extensions.create("straitjacket", StraitjacketExtension::class.java)
      val settings = StraitjacketSettings(this, extension)

      val aggregateCheck = registerAggregateCheckTask(settings.enabled)
      pluginManager.withPlugin("base") {
        tasks.named("check").configure { t -> t.dependsOn(aggregateCheck) }
      }

      val versionCatalogs = extensions.getByType(VersionCatalogsExtension::class.java)
      val versionsByCatalog =
        versionCatalogs.catalogNames.associateWith { catalogName ->
          buildCatalogVersionMap(versionCatalogs.named(catalogName))
        }

      val authoritativeVersions =
        settings.ignoredCatalogs.map { ignored ->
          buildAuthoritativeVersionMap(versionsByCatalog.filterKeys { it !in ignored })
        }

      val resolvedVersions = resolvedVersions(settings.ignoredConfigurations)
      val resolvableConfigs = configurations.matching(Configuration::isCanBeResolved)

      versionsByCatalog.forEach { (catalogName, catalogVersions) ->
        val active = settings.activeFor(catalogName)

        forceCatalogVersions(
          resolvableConfigs = resolvableConfigs,
          catalogName = catalogName,
          catalogVersions = catalogVersions,
          active = active,
          settings = settings,
        )

        val perCatalogCheck =
          registerPerCatalogCheckTask(
            catalogName = catalogName,
            catalogVersions = catalogVersions,
            authoritativeVersions = authoritativeVersions,
            resolvedVersions = resolvedVersions,
            ignoredModules = settings.ignoredModules,
            failOnViolation = settings.failOnViolation,
            active = active,
          )
        aggregateCheck.configure { t -> t.dependsOn(perCatalogCheck) }
      }
    }
}
