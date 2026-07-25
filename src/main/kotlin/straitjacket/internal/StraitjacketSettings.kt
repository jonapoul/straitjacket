package straitjacket.internal

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import straitjacket.StraitjacketExtension

internal class StraitjacketSettings(project: Project, extension: StraitjacketExtension) {
  private val objects: ObjectFactory = project.objects

  val enabled: Provider<Boolean> =
    objects.finalizedProperty(
      project.providers
        .booleanProperty("straitjacket.enabled")
        .orElse(extension.enabled)
        .orElse(true)
    )

  /** No `orElse`, because having no level at all is what silence is. */
  val logForcedVersions: Provider<LogLevel> =
    objects.finalizedProperty(
      project.providers
        .logLevelProperty("straitjacket.logForcedVersions")
        .orElse(extension.logForcedVersions)
    )

  val failOnViolation: Provider<Boolean> =
    project.providers
      .booleanProperty("straitjacket.failOnViolation")
      .orElse(extension.failOnViolation)
      .orElse(true)

  val ignoredCatalogs: Provider<Set<String>> = extension.ignoredCatalogs.convention(emptySet())

  val ignoredConfigurations: Provider<GlobSet> =
    objects.finalizedProperty(extension.ignoredConfigurations.convention(emptySet()).map(::GlobSet))

  /** The raw patterns, which is what the check task takes as an input. */
  val ignoredModules: Provider<Set<String>> = extension.ignoredModules.convention(emptySet())

  val ignoredModuleGlobs: Provider<GlobSet> =
    objects.finalizedProperty(ignoredModules.map(::GlobSet))

  /** Whether Straitjacket acts on the catalog named [catalogName]. */
  fun activeFor(catalogName: String): Provider<Boolean> =
    objects.finalizedProperty(
      enabled.zip(ignoredCatalogs) { isEnabled, ignored -> isEnabled && catalogName !in ignored }
    )

  /**
   * A property with `finalizeValueOnRead`, rather than the [Provider] chain it wraps.
   *
   * Gradle does not memoise a provider, and the substitution rule reads these once per dependency
   * per catalog per resolution, so a plain `zip`/`map` chain re-evaluates the whole thing every
   * time, recompiling the [GlobSet] patterns with it. A finalized property computes once and hands
   * back the same value after that.
   *
   * [failOnViolation], [ignoredCatalogs] and [ignoredModules] stay plain chains deliberately: only
   * the check tasks read them, once each, at execution time.
   *
   * `ResolutionRuleOverheadScenario` counts evaluations and fails if this is lost.
   */
  private inline fun <reified T : Any> ObjectFactory.finalizedProperty(
    value: Provider<T>
  ): Provider<T> = property(T::class.java).value(value).apply { finalizeValueOnRead() }
}
