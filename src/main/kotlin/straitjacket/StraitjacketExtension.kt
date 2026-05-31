package straitjacket

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

public interface StraitjacketExtension {
  public val enabled: Property<Boolean>
  public val ignoredConfigurations: SetProperty<String>
  public val ignoredCatalogs: SetProperty<String>
}
