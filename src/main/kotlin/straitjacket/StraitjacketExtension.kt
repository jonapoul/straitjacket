package straitjacket

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
   * Excludes the given [org.gradle.api.artifacts.Configuration] names from consideration when
   * checking dependencies.
   */
  public val ignoredConfigurations: SetProperty<String>

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
