package straitjacket.internal

import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.VersionCatalog

/**
 * If this dependency exists in the version catalog and the requested version is older than the
 * catalog version, force it up to the catalog version. Newer transitive versions are left alone —
 * the check task catches those.
 */
internal fun DependencyResolveDetails.applyRestriction(versionCatalog: VersionCatalog) {
  val group = requested.group
  val name = requested.name

  for (alias in versionCatalog.libraryAliases) {
    val lib = versionCatalog.findLibrary(alias).orElse(null)?.get()
    lib?.apply {
      if (module.group == group && module.name == name) {
        val catalogVersion = versionConstraint.requiredVersion
        val requestedVersion = requested.version
        if (
          catalogVersion.isNotEmpty() &&
            requestedVersion != null &&
            Version(requestedVersion) < Version(catalogVersion)
        ) {
          useVersion(catalogVersion)
          because(
            "straitjacket: " +
              "version catalog '${versionCatalog.name}' declares $group:$name:$catalogVersion, which is greater " +
              "than $requestedVersion"
          )
        }
        return
      }
    }
  }
}
