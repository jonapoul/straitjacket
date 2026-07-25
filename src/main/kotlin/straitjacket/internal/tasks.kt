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

private fun buildResolvedVersionMap(
  matchingConfigs: NamedDomainObjectSet<Configuration>
): Map<String, List<String>> {
  val versions = mutableMapOf<String, String>()
  val configs = mutableMapOf<String, MutableSet<String>>()
  matchingConfigs.forEach { config ->
    config.incoming.resolutionResult.allComponents { component ->
      val id = component.moduleVersion ?: return@allComponents
      val key = "${id.group}:${id.name}"
      val existing = versions[key]
      if (existing == null || Version(id.version) > Version(existing)) {
        versions[key] = id.version
      }
      configs.getOrPut(key) { mutableSetOf() }.add(config.name)
    }
  }
  return versions.mapValues { (key, version) -> listOf(version) + configs.getValue(key).sorted() }
}
