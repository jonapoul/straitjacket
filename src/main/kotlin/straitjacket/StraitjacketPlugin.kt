package straitjacket

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import straitjacket.internal.GlobSet
import straitjacket.internal.applyRestriction
import straitjacket.internal.buildAuthoritativeVersionMap
import straitjacket.internal.buildCatalogVersionMap
import straitjacket.internal.registerAggregateCheckTask
import straitjacket.internal.registerPerCatalogCheckTask
import straitjacket.internal.resolvedVersions

public class StraitjacketPlugin : Plugin<Project> {
  @Suppress("LongMethod")
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

      // Both compiled once, and held in a property rather than a plain value because the extension
      // has not been configured yet at this point in apply
      val ignoredConfigurations =
        objects
          .property(GlobSet::class.java)
          .value(extension.ignoredConfigurations.convention(emptySet()).map(::GlobSet))
      ignoredConfigurations.finalizeValueOnRead()

      val ignoredModules =
        objects
          .property(GlobSet::class.java)
          .value(extension.ignoredModules.convention(emptySet()).map(::GlobSet))
      ignoredModules.finalizeValueOnRead()

      // Finalized on read like enabled: the rule holds this across every resolution in the build.
      // No orElse, because having no level is what silence is.
      val logForcedVersions =
        objects
          .property(LogLevel::class.java)
          .value(
            providers
              .gradleProperty("straitjacket.logForcedVersions")
              .map(::logLevelOrNull)
              .orElse(extension.logForcedVersions)
          )
      logForcedVersions.finalizeValueOnRead()

      // A plain provider chain, unlike enabled: only the check tasks read this, once each at
      // execution time, so there is no resolution hot path to memoise it for
      val failOnViolation =
        providers
          .gradleProperty("straitjacket.failOnViolation")
          .map(String::toBooleanStrictOrNull)
          .orElse(extension.failOnViolation)
          .orElse(true)

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
          // A substitution rule rather than eachDependency, which reports coordinates only: a
          // project dependency reports the target project's own, so there is no telling it from a
          // module.
          configuration.resolutionStrategy.dependencySubstitution.all { substitution ->
            if (active.get() && configurationName !in ignoredConfigurations.get()) {
              substitution.applyRestriction(
                catalogName = catalogName,
                configurationName = configurationName,
                catalogVersions = catalogVersions,
                ignoredModules = ignoredModules.get(),
                logForcedVersions = logForcedVersions,
              )
            }
          }
        }

        val perCatalogCheck =
          registerPerCatalogCheckTask(
            catalogName = catalogName,
            catalogVersions = catalogVersions,
            authoritativeVersions = authoritativeVersions,
            resolvedVersions = resolvedVersions,
            ignoredModules = extension.ignoredModules,
            failOnViolation = failOnViolation,
            active = active,
          )
        aggregateCheck.configure { it.dependsOn(perCatalogCheck) }
      }
    }

  private fun Configuration.shouldBeChecked(ignoredConfigurations: Provider<GlobSet>): Boolean =
    isCanBeResolved && name !in ignoredConfigurations.get()

  // Null leaves the provider absent, so a string naming no level falls back to the extension, as an
  // unparseable boolean property does
  private fun logLevelOrNull(string: String): LogLevel? =
    LogLevel.entries.firstOrNull { level -> level.name.equals(string, ignoreCase = true) }
}
