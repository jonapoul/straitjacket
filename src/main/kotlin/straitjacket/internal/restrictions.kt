package straitjacket.internal

import org.gradle.api.artifacts.DependencyResolveDetails

/**
 * Forces this dependency up to the version [catalogName] declares for it, if that is higher than
 * the version the dependency is currently headed for.
 *
 * Every catalog in the build gets its own rule, and they all run against the same dependency, so
 * the comparison is against [DependencyResolveDetails.getTarget] rather than
 * [DependencyResolveDetails.getRequested]. `requested` always reports the version the build
 * originally asked for and never what an earlier rule already forced, so reading it would make each
 * catalog decide in isolation from the same starting point and let the last rule to run pull the
 * version back down. Reading the target means each rule builds on the previous one's decision, and
 * the highest version any catalog declares wins whatever order the catalogs were registered in.
 */
internal fun DependencyResolveDetails.applyRestriction(
  catalogName: String,
  catalogVersions: Map<String, String>,
) {
  val group = target.group
  val name = target.name

  val catalogVersion = catalogVersions["$group:$name"] ?: return
  val targetVersion = target.version ?: return

  if (Version(targetVersion) < Version(catalogVersion)) {
    useVersion(catalogVersion)
    because(
      "straitjacket: " +
        "version catalog '$catalogName' declares $group:$name:$catalogVersion, which is greater " +
        "than $targetVersion"
    )
  }
}
