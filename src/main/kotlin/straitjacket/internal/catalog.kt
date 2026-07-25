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
