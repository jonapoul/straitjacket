package straitjacket.internal

/**
 * A comparable version string. Splits on "." and "-", comparing numeric segments numerically and
 * non-numeric segments (e.g. "alpha", "jre") lexicographically.
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
      else -> comparePreReleaseParts(preA!!, preB!!) // Both have pre-releases
    }
  }

  private fun splitVersionAndPreRelease(version: String): Pair<String, String?> {
    val dashIndex = version.indexOf('-')
    return if (dashIndex == -1) {
      version to null
    } else {
      version.substring(0, dashIndex) to version.substring(dashIndex + 1)
    }
  }

  private fun compareNumericParts(mainA: String, mainB: String): Int {
    val partsA = mainA.split(".")
    val partsB = mainB.split(".")
    val maxLen = maxOf(partsA.size, partsB.size)

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
    val minLen = minOf(partsA.size, partsB.size)

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
