package straitjacket

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import straitjacket.internal.Version

/**
 * Validates that all dependencies in the allowed [org.gradle.api.artifacts.Configuration]s match
 * the version declared in the given version catalog files. If no match, assume it's valid and just
 * a passthrough dependency.
 */
@CacheableTask
public abstract class StraitjacketCheck : DefaultTask() {
  /** Map of `"$group:$name"` to `"$catalogVersion"`. */
  @get:Input public abstract val catalogVersions: MapProperty<String, String>

  /** Map of `"$group:$name"` to `[resolvedVersion, config1, config2, ...]`. */
  @get:Input public abstract val resolvedVersions: MapProperty<String, List<String>>

  /** Generated report file. If successful, this file will be created with no contents. */
  @get:OutputFile public abstract val reportFile: RegularFileProperty

  /** Runs the check. */
  @TaskAction
  public fun execute() {
    val catalog = catalogVersions.get()
    val resolved = resolvedVersions.get()
    val violations = mutableListOf<String>()

    for ((coordinate, parts) in resolved) {
      val catalogVersion = catalog[coordinate] ?: continue
      val resolvedVersion = parts.first()
      val configNames = parts.drop(1)
      if (Version(resolvedVersion) > Version(catalogVersion)) {
        val configNameStr = configNames.joinToString(", ")
        violations += "$coordinate:$catalogVersion -> $resolvedVersion (in $configNameStr)"
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
            "Update your version catalog or add these configurations to ignoredConfigurations."
          )
        }
      }

    reportFile.get().asFile.writeText(report)

    if (violations.isNotEmpty()) {
      throw GradleException(report)
    }
  }
}
