package straitjacket.internal

import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
  matchingConfigs: NamedDomainObjectSet<Configuration>,
  active: Provider<Boolean>,
): TaskProvider<StraitjacketCheck> {
  val taskName = "straitjacketCheck${catalogName.capitalized()}"
  return tasks.register(taskName, StraitjacketCheck::class.java) { t ->
    t.group = VERIFICATION_GROUP
    t.description =
      "Check that no resolved dependencies are newer than declared in the '$catalogName' version catalog."
    t.catalogVersions.set(catalogVersions)
    t.resolvedVersions.set(provider { buildResolvedVersionMap(matchingConfigs) })
    t.reportFile.set(layout.buildDirectory.file("reports/straitjacket/$catalogName.txt"))
    t.onlyIf { active.get() }
  }
}

/**
 * Returns "$group:$name" -> resolved version -> the sorted names of the configurations which
 * resolved that version.
 *
 * A module can resolve to different versions in different configurations, so the version has to be
 * part of the key. Collapsing to one version per module would report the wrong configurations
 * against it, and would hide any other offending version it resolved to.
 */
private fun buildResolvedVersionMap(
  matchingConfigs: NamedDomainObjectSet<Configuration>
): Map<String, Map<String, List<String>>> {
  val configs = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
  matchingConfigs.forEach { config ->
    config.incoming.resolutionResult.allComponents { component ->
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
