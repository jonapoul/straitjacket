package straitjacket.internal

/**
 * A comparable version string following SemVer precedence rules.
 *
 * The value is split into a main version and an optional pre-release identifier at the first "-"
 * (e.g. "1.0.0-alpha.1" -> main "1.0.0", pre-release "alpha.1"):
 * - Main versions are compared by their dot-separated numeric segments, with missing segments
 *   treated as 0 (so "1.2" == "1.2.0"). Non-numeric main segments are also treated as 0.
 * - A pre-release version has lower precedence than the matching release ("1.0.0-alpha" < "1.0.0").
 * - Pre-release identifiers are compared field-by-field: numeric fields compare numerically,
 *   numeric fields rank below non-numeric fields, non-numeric fields compare lexicographically, and
 *   a larger set of fields wins when all shared fields are equal ("alpha.1" > "alpha").
 *
 * Build metadata ("+...") is not stripped and may compare incorrectly.
 */
@JvmInline
internal value class Version(val value: String) : Comparable<Version> {

  override fun compareTo(other: Version): Int {
    // 1. Separate the main version from the pre-release identifier
    // "1.0.0-alpha.1" -> main = "1.0.0", preRelease = "alpha.1"
    val (mainA, preA) = splitVersionAndPreRelease(this.value)
    val (mainB, preB) = splitVersionAndPreRelease(other.value)

    // 2. Compare the main numeric components (Major.Minor.Patch)
    val mainCmp = compareNumericParts(mainA, mainB)
    if (mainCmp != 0) return mainCmp

    // 3. If main versions are equal, handle SemVer Pre-release precedence
    return when {
      preA == null && preB == null -> 0

      // Pre-release is LESS than normal release (e.g., 1.0.0-alpha < 1.0.0)
      preA != null && preB == null -> -1

      // Normal release is GREATER than pre-release
      preA == null && preB != null -> 1

      // Both have pre-releases
      else -> comparePreReleaseParts(preA = requireNotNull(preA), preB = requireNotNull(preB))
    }
  }

  private fun splitVersionAndPreRelease(version: String): Pair<String, String?> {
    val dashIndex = version.indexOf('-')
    return if (dashIndex == -1) {
      version to null
    } else {
      version.substring(startIndex = 0, endIndex = dashIndex) to version.substring(dashIndex + 1)
    }
  }

  private fun compareNumericParts(mainA: String, mainB: String): Int {
    val partsA = mainA.split(".")
    val partsB = mainB.split(".")
    val maxLen = maxOf(a = partsA.size, b = partsB.size)

    for (i in 0 until maxLen) {
      val numA = partsA.getOrNull(i)?.toLongOrNull() ?: 0L
      val numB = partsB.getOrNull(i)?.toLongOrNull() ?: 0L
      val cmp = numA.compareTo(numB)
      if (cmp != 0) return cmp
    }
    return 0
  }

  private fun comparePreReleaseParts(preA: String, preB: String): Int {
    // SemVer pre-releases can be dot-separated (e.g., alpha.1)
    val partsA = preA.split(".")
    val partsB = preB.split(".")
    val minLen = minOf(a = partsA.size, b = partsB.size)

    for (i in 0 until minLen) {
      val partA = partsA[i]
      val partB = partsB[i]

      val numA = partA.toLongOrNull()
      val numB = partB.toLongOrNull()

      val cmp =
        when {
          // Numeric identifiers always have lower precedence than non-numeric identifiers
          numA != null && numB != null -> numA.compareTo(numB)

          numA != null && numB == null -> -1

          numA == null && numB != null -> 1

          // Both textual (lexicographical)
          else -> partA.compareTo(partB)
        }
      if (cmp != 0) return cmp
    }

    // If all existing parts are equal, the one with MORE pre-release fields is greater
    // e.g., 1.0.0-alpha.1 > 1.0.0-alpha
    return partsA.size.compareTo(partsB.size)
  }

  override fun toString(): String = value
}
