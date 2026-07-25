package straitjacket

import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

public interface StraitjacketExtension {
  /**
   * Set to false to disable Straitjacket entirely: both the forcing of under-versioned dependencies
   * up to their catalog version and the `straitjacketCheck` tasks. Defaults to true.
   *
   * If the `straitjacket.enabled` Gradle property is set to `true` or `false`, it takes priority
   * over this value.
   */
  public val enabled: Property<Boolean>

  /**
   * Set to false to have the check tasks log their report at `WARN` and succeed, rather than fail
   * the build. Defaults to true.
   *
   * Useful for adopting Straitjacket on a project that is not clean yet, where failing on day one
   * is not an option but the drift still wants watching.
   *
   * A task that succeeds is up to date on the next build and does not log its warning again, so the
   * report file is the record. It is written either way, and its path is in the warning.
   *
   * If the `straitjacket.failOnViolation` Gradle property is set to `true` or `false`, it takes
   * priority over this value.
   */
  public val failOnViolation: Property<Boolean>

  /**
   * The level at which to log every dependency Straitjacket forces up to its catalog version. Unset
   * by default, which is silent.
   *
   * Forcing otherwise leaves no trace beyond the selection reason, which you only see by asking for
   * a dependency insight report. Setting a level says what moved and where:
   * ```
   * Straitjacket forced com.website:bar 2.3.4 -> 2.5.0 in runtimeClasspath (catalog 'libs')
   * ```
   *
   * Which level to pick depends on what you want it for. [LogLevel.INFO] keeps it out of a normal
   * build and puts it behind `--info`, for when you are working something out. [LogLevel.LIFECYCLE]
   * or higher makes it a permanent part of the build log, for a record in CI.
   *
   * One line per configuration that resolved the dependency, so a module forced in four classpaths
   * says so four times. Logging happens during resolution, so a build that resolves nothing, having
   * found its tasks up to date, logs nothing.
   *
   * Call `unset()` to silence a level a convention plugin has set. The
   * `straitjacket.logForcedVersions` Gradle property takes priority over this value, and is matched
   * to a level by name without regard to case.
   */
  public val logForcedVersions: Property<LogLevel>

  /**
   * Excludes the matching [org.gradle.api.artifacts.Configuration] names from both halves of
   * Straitjacket: dependencies resolved by those configurations are neither forced up to their
   * catalog version nor reported by the check tasks.
   *
   * Each entry is a configuration name, in which `*` stands for any run of characters:
   * ```kotlin
   * straitjacket {
   *   ignoredConfigurations.add("someConfiguration")   // one configuration
   *   ignoredConfigurations.add("*UnitTestRuntimeClasspath")   // every variant's, on Android
   * }
   * ```
   *
   * A name without a `*` matches exactly, so patterns only ever widen what an entry covers. Reach
   * for them when the configurations you mean are generated per variant and enumerating them would
   * mean keeping a list in sync with your variants.
   *
   * Prefer [ignoredModules] when a single dependency is the problem. Ignoring a configuration gives
   * up on every other module it resolves too.
   */
  public val ignoredConfigurations: SetProperty<String>

  /**
   * Excludes the given modules from both halves of Straitjacket: they are neither forced up to
   * their catalog version nor reported by the check tasks, whichever configuration they turn up in.
   *
   * Each entry is a `"$group:$name"` coordinate, in which `*` stands for any run of characters:
   * ```kotlin
   * straitjacket {
   *   ignoredModules.add("com.website:bar")   // one module
   *   ignoredModules.add("com.website:*")     // every module in a group
   *   ignoredModules.add("*:bar")             // every module with that name, whatever its group
   * }
   * ```
   *
   * Use this rather than [ignoredConfigurations] when one dependency is the problem. Ignoring a
   * configuration gives up on every other module it resolves too.
   */
  public val ignoredModules: SetProperty<String>

  /**
   * If you have multiple catalog files registered, use this property to exclude any subset of them.
   *
   * E.g. if you have a file called "someOtherLibs.versions.toml":
   * ```kotlin
   * straitjacket {
   *   ignoredCatalogs.add("someOtherLibs")
   * }
   * ```
   */
  public val ignoredCatalogs: SetProperty<String>
}
