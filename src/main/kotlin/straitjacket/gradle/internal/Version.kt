package straitjacket.gradle.internal

/**
 * A comparable version string. Splits on "." and "-", comparing numeric segments numerically and
 * non-numeric segments (e.g. "alpha", "jre") lexicographically.
 */
@JvmInline
internal value class Version(val value: String) : Comparable<Version> {
  override fun compareTo(other: Version): Int {
    val partsA = value.split(".", "-")
    val partsB = other.value.split(".", "-")
    val maxLen = maxOf(partsA.size, partsB.size)
    for (i in 0 until maxLen) {
      val partA = partsA.getOrElse(i) { "0" }
      val partB = partsB.getOrElse(i) { "0" }
      val numA = partA.toLongOrNull()
      val numB = partB.toLongOrNull()
      val cmp =
        if (numA != null && numB != null) {
          numA.compareTo(numB)
        } else {
          partA.compareTo(partB)
        }
      if (cmp != 0) return cmp
    }
    return 0
  }

  override fun toString(): String = value
}
