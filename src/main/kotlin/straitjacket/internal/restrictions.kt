package straitjacket.internal

import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector

/**
 * Forces this dependency up to the version [catalogName] declares for it, if that is higher than
 * the version it is currently headed for.
 *
 * Project dependencies are left alone. Substituting a version for one swaps the project out for a
 * module that only exists once published.
 */
internal fun DependencySubstitution.applyRestriction(
  catalogName: String,
  catalogVersions: Map<String, String>,
) {
  val target = currentTarget() as? ModuleComponentSelector ?: return

  val group = target.group
  val name = target.module

  val catalogVersion = catalogVersions["$group:$name"] ?: return
  val targetVersion = target.version

  if (Version(targetVersion) < Version(catalogVersion)) {
    useTarget(
      target.withVersion(catalogVersion),
      "straitjacket: " +
        "version catalog '$catalogName' declares $group:$name:$catalogVersion, which is greater " +
        "than $targetVersion",
    )
  }
}

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
