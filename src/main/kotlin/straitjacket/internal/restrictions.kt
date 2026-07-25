package straitjacket.internal

import org.gradle.api.artifacts.DependencyResolveDetails

internal fun DependencyResolveDetails.applyRestriction(
  catalogName: String,
  catalogVersions: Map<String, String>,
) {
  val group = requested.group
  val name = requested.name

  val catalogVersion = catalogVersions["$group:$name"] ?: return
  val requestedVersion = requested.version ?: return

  if (Version(requestedVersion) < Version(catalogVersion)) {
    useVersion(catalogVersion)
    because(
      "straitjacket: " +
        "version catalog '$catalogName' declares $group:$name:$catalogVersion, which is greater " +
        "than $requestedVersion"
    )
  }
}
