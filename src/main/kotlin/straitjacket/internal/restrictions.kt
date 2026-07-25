package straitjacket.internal

import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector

/**
 * Forces this dependency up to the version [catalogName] declares for it, if that is higher than
 * the version the dependency is currently headed for.
 *
 * Rules run against the selector the dependency is currently headed for rather than the one the
 * build originally asked for. Every catalog in the build gets its own rule, and they all run
 * against the same dependency, so reading the requested selector would make each catalog decide in
 * isolation from the same starting point and let the last rule to run pull the version back down.
 * Reading the current target means each rule builds on the previous one's decision, and the highest
 * version any catalog declares wins whatever order the catalogs were registered in. It also means a
 * rule from another plugin that already raised a version is left alone rather than undone.
 *
 * A project dependency is left alone entirely. Its selector is a [ProjectComponentSelector], and
 * substituting a version for it would swap the project out for an external module that only exists
 * once published.
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
 * The selector this dependency is currently headed for, which is what an earlier rule substituted
 * or the requested selector if nothing has touched it yet.
 *
 * [DependencySubstitution] only publishes `requested`, which always reports what the build asked
 * for and never what an earlier rule already decided. The internal interface is the same one Gradle
 * itself reads through for `eachDependency`, whose `DependencyResolveDetails.getTarget` is this
 * value converted to coordinates. Coordinates are what the type-blind version of this rule used to
 * work from, and losing the selector type is exactly what let a project dependency be forced into a
 * module that does not exist.
 */
private fun DependencySubstitution.currentTarget() = (this as DependencySubstitutionInternal).target

/**
 * The same selector at a different version.
 *
 * Substituting by `"$group:$name:$version"` notation would build a bare selector and drop the
 * attributes and capability selectors this one carries, which is how a `platform` dependency stops
 * being a platform and a `testFixtures` one stops asking for test fixtures. Gradle's own
 * `useVersion` carries both across, so this does too.
 */
private fun ModuleComponentSelector.withVersion(version: String): ModuleComponentSelector =
  DefaultModuleComponentSelector.newSelector(
    moduleIdentifier,
    DefaultImmutableVersionConstraint.of(version),
    attributes,
    capabilitySelectors,
  )
