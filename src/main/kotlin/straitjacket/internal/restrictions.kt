package straitjacket.internal

import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.VersionCatalog

internal fun DependencyResolveDetails.applyRestriction(versionCatalog: VersionCatalog) {
  val group = requested.group
  val name = requested.name

  for (alias in versionCatalog.libraryAliases) {
    val lib = versionCatalog.findLibrary(alias).orElse(null)?.get() ?: continue
    if (lib.module.group == group && lib.module.name == name) {
      val catalogVersion = lib.versionConstraint.requiredVersion
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
