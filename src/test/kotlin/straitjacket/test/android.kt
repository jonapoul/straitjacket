@file:Suppress("ImplicitUnitReturnType") // https://github.com/detekt/detekt/issues/9422

package straitjacket.test

import blueprint.test.FileTree
import blueprint.test.localProperties

// Nothing in the AGP scenarios compiles Android sources, so these only have to be values AGP will
// accept and any SDK the tests could run against will have.
internal const val COMPILE_SDK = 36
internal const val MIN_SDK = 28

/**
 * A fixture task printing the version okio resolved to in every classpath the project has, so the
 * AGP scenarios can assert forcing reached each of them rather than only that the check passed.
 * Resolving in a `doLast` block means whoever runs it has to opt out of the configuration cache.
 */
internal val PRINT_RESOLVED_OKIO =
  """
  tasks.register("printResolvedOkio") {
    val names = configurations.names.filter {
      it.endsWith("CompileClasspath") || it.endsWith("RuntimeClasspath")
    }
    doLast {
      names.sorted().forEach { name ->
        configurations.getByName(name).incoming.resolutionResult.allComponents {
          val mv = moduleVersion
          if (mv != null && mv.group == "com.squareup.okio" && mv.name == "okio") {
            logger.lifecycle("RESOLVED_OKIO=" + name + ":" + mv.version)
          }
        }
      }
    }
  }
  """
    .trimIndent()

/** AGP locates the SDK through `local.properties`, which the harness does not write itself. */
internal fun FileTree.Builder.androidLocalProperties() =
  localProperties(
    """
    sdk.dir=${ANDROID_SDK.invariantSeparatorsPath}
    """
      .trimIndent()
  )
