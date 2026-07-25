package straitjacket

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import straitjacket.internal.GlobSet
import straitjacket.internal.Version

@CacheableTask
public abstract class StraitjacketCheck : DefaultTask() {
  // Map of "$group:$name" to "$catalogVersion"
  @get:Input public abstract val catalogVersions: MapProperty<String, String>

  // Map of "$group:$name" to [highestDeclaredVersion, declaringCatalogName], across every catalog
  // in the build that Straitjacket is not ignoring. A module this catalog declares can be declared
  // higher by another catalog, and the version that wins there is the one the forcing side aims
  // for, so it is the one to judge the resolved version against. Empty falls back to this
  // catalog's own declaration.
  @get:Input public abstract val authoritativeVersions: MapProperty<String, List<String>>

  // Map of "$group:$name" to a map of "$resolvedVersion" to the configurations which resolved it.
  // One module can appear under several versions, one per version it resolved to.
  @get:Input public abstract val resolvedVersions: MapProperty<String, Map<String, List<String>>>

  // "$group:$name" patterns, in which "*" stands for any run of characters, for modules this check
  // must not report however they resolve. The forcing side leaves the same modules alone.
  @get:Input public abstract val ignoredModules: SetProperty<String>

  // Whether a violation fails the build or is only logged. An input so that turning it off reruns
  // the task rather than leaving the report from a build that did fail.
  @get:Input public abstract val failOnViolation: Property<Boolean>

  // Generated report file. If successful, this file will be created with no contents
  @get:OutputFile public abstract val reportFile: RegularFileProperty

  @TaskAction
  public fun execute() {
    val catalog = catalogVersions.get()
    val authoritative = authoritativeVersions.get()
    val resolved = resolvedVersions.get()
    val ignored = GlobSet(ignoredModules.get())
    val violations = mutableListOf<String>()

    for ((coordinate, versions) in resolved) {
      if (coordinate in ignored) continue
      val catalogVersion = catalog[coordinate] ?: continue
      val declaredVersion = authoritative[coordinate]?.first() ?: catalogVersion
      // Only worth naming a catalog when it is not the one this task checks, otherwise the
      // message repeats what the task name already says.
      val declaredBy =
        authoritative[coordinate]
          ?.getOrNull(1)
          ?.takeIf { declaredVersion != catalogVersion }
          ?.let { " (declared by catalog '$it')" }
          .orEmpty()
      for ((resolvedVersion, configNames) in versions) {
        if (Version(resolvedVersion) > Version(declaredVersion)) {
          val configNameStr = configNames.joinToString(", ")
          violations +=
            "$coordinate:$declaredVersion$declaredBy -> $resolvedVersion (in $configNameStr)"
        }
      }
    }

    val report =
      if (violations.isEmpty()) {
        ""
      } else {
        buildString {
          appendLine(
            "Straitjacket found dependencies resolved to versions newer than the version catalog declares:"
          )
          appendLine()
          violations.sorted().forEach { appendLine("  $it") }
          appendLine()
          appendLine(
            "Update your version catalog, or exclude them with ignoredModules or ignoredConfigurations."
          )
        }
      }

    val file = reportFile.get().asFile
    file.writeText(report)

    if (violations.isEmpty()) return

    if (failOnViolation.get()) {
      throw GradleException(report)
    } else {
      // This task succeeds, so it is up to date on the next build and this warning is not logged
      // again. Point at the report file, which is the record that survives.
      logger.warn(
        "$report\n\nNot failing the build because failOnViolation is off. " +
          "See the Straitjacket report at ${file.absolutePath}"
      )
    }
  }
}
