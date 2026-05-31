package straitjacket

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.SetProperty
import straitjacket.internal.applyRestriction
import straitjacket.internal.registerAggregateCheckTask
import straitjacket.internal.registerPerCatalogCheckTask

/**
 * Keeps resolved dependency versions aligned with the project's Gradle version catalog(s).
 *
 * For every resolvable [Configuration] — except those named in
 * [StraitjacketExtension.ignoredConfigurations] — and every registered version catalog — except
 * those in [StraitjacketExtension.ignoredCatalogs] — the plugin:
 * - forces any dependency requested *below* its catalog version up to the catalog version, and
 * - registers a `straitjacketCheck<Catalog>` task that fails the build if a dependency resolves to
 *   a version *newer* than the catalog declares, e.g. because a transitive dependency dragged it
 *   up.
 *
 * Both behaviours are skipped for a catalog when [StraitjacketExtension.enabled] is false or the
 * catalog is ignored. The per-catalog checks are aggregated under a single `straitjacketCheck`
 * task, which is wired into the standard `check` lifecycle task whenever the `base` plugin is
 * applied.
 *
 * Configure behaviour via the [StraitjacketExtension] registered as `straitjacket`.
 */
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

      val aggregateCheck = registerAggregateCheckTask(enabled)

      pluginManager.withPlugin("base") {
        tasks.named("check").configure { t -> t.dependsOn(aggregateCheck) }
      }

      // Register resolution strategy and check task for every available catalog.
      versionCatalogs.catalogNames.forEach { catalogName ->
        val versionCatalog = versionCatalogs.named(catalogName)
        val isIgnored = ignoredCatalogs.map { ignored -> catalogName in ignored }

        // Whether Straitjacket should act on this catalog at all: globally enabled and not ignored.
        // Lazily combined so it is evaluated at execution time rather than reading both providers
        // eagerly. Gates both the version forcing and the check task.
        val active = enabled.zip(isIgnored) { isEnabled, ignored -> isEnabled && !ignored }

        matchingConfigs.configureEach { configuration ->
          configuration.resolutionStrategy.eachDependency { details ->
            if (active.get()) {
              details.applyRestriction(versionCatalog)
            }
          }
        }

        val perCatalogCheck =
          registerPerCatalogCheckTask(
            catalogName = catalogName,
            versionCatalog = provider { versionCatalog },
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
