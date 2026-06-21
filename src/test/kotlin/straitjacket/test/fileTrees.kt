@file:Suppress("ImplicitUnitReturnType") // https://github.com/detekt/detekt/issues/9422

package straitjacket.test

import blueprint.test.DEFAULT_REPOSITORIES_KTS
import blueprint.test.FileTree
import org.intellij.lang.annotations.Language

internal fun FileTree.Builder.settingsGradleKts(
  @Language("kotlin") contents: String = DEFAULT_REPOSITORIES_KTS
) = "settings.gradle.kts"(contents)

internal fun FileTree.Builder.buildGradleKts(@Language("kotlin") contents: String) =
  "build.gradle.kts"(contents)

internal fun FileTree.Builder.libsVersionsToml(@Language("toml") contents: String) =
  ("gradle" / "libs.versions.toml")(contents)
