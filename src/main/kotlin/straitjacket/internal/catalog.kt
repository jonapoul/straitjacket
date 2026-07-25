package straitjacket.internal

import org.gradle.api.artifacts.VersionCatalog

/**
 * Maps every module the [catalog] declares a version for to that version, keyed by
 * `"$group:$name"`.
 *
 * The forcing side and the checking side both read a map built here, so iteration order of
 * [VersionCatalog.getLibraryAliases] can never make them disagree about what the catalog declares.
 * That matters because the same module can be declared under more than one alias with different
 * versions; duplicates resolve to the highest declared version. The plugin only ever forces
 * versions up, so the highest declared version is the only choice it cannot then report as a
 * violation of its own forcing.
 */
internal fun buildCatalogVersionMap(catalog: VersionCatalog): Map<String, String> {
  val map = mutableMapOf<String, String>()
  for (alias in catalog.libraryAliases) {
    val lib = catalog.findLibrary(alias).orElse(null)?.get() ?: continue
    val version = lib.versionConstraint.requiredVersion
    val key = "${lib.module.group}:${lib.module.name}"
    val existing = map[key]
    if (version.isNotEmpty() && (existing == null || Version(version) > Version(existing))) {
      map[key] = version
    }
  }
  return map
}

/**
 * Merges the per-catalog maps built by [buildCatalogVersionMap], keyed by catalog name, into one
 * map of `"$group:$name"` to `[highestDeclaredVersion, declaringCatalogName]`.
 *
 * A module can be declared by more than one catalog at different versions, and the forcing side
 * settles that the same way it settles duplicate aliases, by taking the highest. The check tasks
 * read this map so that no catalog reports a violation for a version another catalog in the same
 * build declares, which would be Straitjacket failing the build on a bump it performed itself.
 *
 * Callers must leave ignored catalogs out of [catalogVersions]: a catalog Straitjacket is not
 * enforcing does not get to make a version authoritative for the catalogs it is.
 */
internal fun buildAuthoritativeVersionMap(
  catalogVersions: Map<String, Map<String, String>>
): Map<String, List<String>> {
  val map = mutableMapOf<String, List<String>>()
  for ((catalogName, versions) in catalogVersions) {
    for ((coordinate, version) in versions) {
      val existing = map[coordinate]?.first()
      if (existing == null || Version(version) > Version(existing)) {
        map[coordinate] = listOf(version, catalogName)
      }
    }
  }
  return map
}
