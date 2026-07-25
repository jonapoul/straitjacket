package straitjacket.internal

import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector

/**
 * Registers the rule that forces the dependencies of every configuration in [resolvableConfigs] up
 * to the version [catalogName] declares for them.
 *
 * [settings] is read into locals here rather than inside the rule, which the configuration cache
 * serializes and which must therefore capture nothing beyond plain values and providers.
 */
internal fun forceCatalogVersions(
  resolvableConfigs: NamedDomainObjectSet<Configuration>,
  catalogName: String,
  catalogVersions: Map<String, String>,
  active: Provider<Boolean>,
  settings: StraitjacketSettings,
) {
  val ignoredConfigurations = settings.ignoredConfigurations
  val ignoredModules = settings.ignoredModuleGlobs
  val logForcedVersions = settings.logForcedVersions

  resolvableConfigs.configureEach { configuration ->
    // The name, not the Configuration, so the rule stays configuration cache friendly
    val configurationName = configuration.name
    // A substitution rule rather than eachDependency, which reports coordinates only: a project
    // dependency reports the target project's own, so there is no telling it from a module.
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
}

/**
 * Forces this dependency up to the version [catalogName] declares for it, if that is higher than
 * the version it is currently headed for.
 *
 * Modules matching [ignoredModules] are left alone, as are project dependencies. Substituting a
 * version for a project swaps it out for a module that only exists once published.
 *
 * [logForcedVersions] is only read once a force is going ahead, so a build that leaves it unset
 * pays nothing for it on a path that runs once per dependency per catalog per resolution.
 */
private fun DependencySubstitution.applyRestriction(
  catalogName: String,
  configurationName: String,
  catalogVersions: Map<String, String>,
  ignoredModules: GlobSet,
  logForcedVersions: Provider<LogLevel>,
) {
  val target = currentTarget() as? ModuleComponentSelector ?: return

  val group = target.group
  val name = target.module
  val coordinate = "$group:$name"
  if (coordinate in ignoredModules) return

  val catalogVersion = catalogVersions[coordinate] ?: return
  val targetVersion = target.version

  if (Version(targetVersion) < Version(catalogVersion)) {
    useTarget(
      target.withVersion(catalogVersion),
      "straitjacket: " +
        "version catalog '$catalogName' declares $group:$name:$catalogVersion, which is greater " +
        "than $targetVersion",
    )

    // Throws if the Gradle property named no level. Having no level at all is what silence is
    val level = logForcedVersions.orNull
    if (level != null) {
      // One line per configuration that resolved it, so a module forced in four classpaths says so
      // four times. Collapsing them would need state shared across resolutions, which the
      // configuration cache does not allow.
      LOGGER.log(
        level,
        "Straitjacket forced $coordinate $targetVersion -> $catalogVersion in $configurationName " +
          "(catalog '$catalogName')",
      )
    }
  }
}

// Fetched by name rather than captured from the Project, which a resolution rule must not hold on
// to
private val LOGGER = Logging.getLogger("straitjacket")

/**
 * The selector an earlier rule substituted, or the requested one if nothing has touched it yet.
 *
 * Every catalog gets its own rule against the same dependency, so reading the public `requested`
 * would let the last rule to run pull the version back down.
 */
private fun DependencySubstitution.currentTarget() = (this as DependencySubstitutionInternal).target

/**
 * The same selector at a different version.
 *
 * `"group:name:version"` notation would build a bare one and drop the attributes and capability
 * selectors this carries, so a platform would stop being a platform.
 */
private fun ModuleComponentSelector.withVersion(version: String): ModuleComponentSelector =
  DefaultModuleComponentSelector.newSelector(
    moduleIdentifier,
    DefaultImmutableVersionConstraint.of(version),
    attributes,
    capabilitySelectors,
  )
