package straitjacket.internal

import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import straitjacket.StraitjacketCheck

internal fun Project.registerAggregateCheckTask(enabled: Provider<Boolean>): TaskProvider<*> =
  tasks.register("straitjacketCheck") { t ->
    t.group = VERIFICATION_GROUP
    t.description = "Run all Straitjacket version catalog checks."
    t.onlyIf { enabled.get() }
  }

internal fun Project.registerPerCatalogCheckTask(
  catalogName: String,
  catalogVersions: Map<String, String>,
  authoritativeVersions: Provider<Map<String, List<String>>>,
  resolvedVersions: Provider<Map<String, Map<String, List<String>>>>,
  ignoredModules: Provider<Set<String>>,
  active: Provider<Boolean>,
): TaskProvider<StraitjacketCheck> {
  val taskName = "straitjacketCheck${catalogName.capitalized()}"
  return tasks.register(taskName, StraitjacketCheck::class.java) { t ->
    t.group = VERIFICATION_GROUP
    t.description =
      "Check that no resolved dependencies are newer than declared in the '$catalogName' version catalog."
    t.catalogVersions.set(catalogVersions)
    t.authoritativeVersions.set(authoritativeVersions)
    t.resolvedVersions.set(resolvedVersions)
    t.ignoredModules.set(ignoredModules)
    t.reportFile.set(layout.buildDirectory.file("reports/straitjacket/$catalogName.txt"))
    t.onlyIf { active.get() }
  }
}

/**
 * Every per-catalog check task judges the same resolved graph, so the walk happens once for
 * whichever task asks first and the rest reuse the answer. Gradle does not memoise a provider, and
 * a walk visits every resolvable configuration in the project, so without the [lazy] the cost is
 * paid once per catalog.
 */
internal fun Project.resolvedVersions(
  checkedConfigs: NamedDomainObjectSet<Configuration>
): Provider<Map<String, Map<String, List<String>>>> {
  val resolved = lazy { buildResolvedVersionMap(checkedConfigs) }
  return provider { resolved.value }
}

/**
 * Returns "$group:$name" -> resolved version -> the sorted names of the configurations which
 * resolved that version.
 *
 * A module can resolve to different versions in different configurations, so the version has to be
 * part of the key. Collapsing to one version per module would report the wrong configurations
 * against it, and would hide any other offending version it resolved to.
 *
 * Projects are skipped. One reports its own coordinates here, and it builds at the version it
 * declares, so neither remedy the check suggests could act on it.
 */
private fun buildResolvedVersionMap(
  checkedConfigs: NamedDomainObjectSet<Configuration>
): Map<String, Map<String, List<String>>> {
  val configs = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
  checkedConfigs.forEach { config ->
    config.incoming.resolutionResult.allComponents { component ->
      if (component.id is ProjectComponentIdentifier) return@allComponents
      val id = component.moduleVersion ?: return@allComponents
      val key = "${id.group}:${id.name}"
      configs
        .getOrPut(key) { mutableMapOf() }
        .getOrPut(id.version) { mutableSetOf() }
        .add(config.name)
    }
  }
  return configs.mapValues { (_, byVersion) ->
    byVersion.mapValues { (_, configNames) -> configNames.sorted() }
  }
}
