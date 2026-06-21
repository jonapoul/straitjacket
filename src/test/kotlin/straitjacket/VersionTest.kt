package straitjacket

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isZero
import kotlin.test.Test
import straitjacket.internal.Version

class VersionTest {
  @Test
  fun `two identical versions compare as equal`() {
    assertThatVersion("1.2.3").isEqualToVersion("1.2.3")
  }

  @Test
  fun `a higher major version is greater`() {
    assertThatVersion("2.0.0").isGreaterThanVersion("1.0.0")
  }

  @Test
  fun `a lower major version is less`() {
    assertThatVersion("1.0.0").isLessThanVersion("2.0.0")
  }

  @Test
  fun `a higher minor version is greater`() {
    assertThatVersion("1.3.0").isGreaterThanVersion("1.2.0")
  }

  @Test
  fun `a higher patch version is greater`() {
    assertThatVersion("1.2.4").isGreaterThanVersion("1.2.3")
  }

  @Test
  fun `a version with more segments is greater than its prefix`() {
    assertThatVersion("1.2.3.1").isGreaterThanVersion("1.2.3")
    assertThatVersion("1.2.3").isLessThanVersion("1.2.3.1")
  }

  @Test
  fun `missing trailing segments are treated as zero`() {
    assertThatVersion("1.2.0").isEqualToVersion("1.2")
  }

  @Test
  fun `an alpha pre-release is less than a beta pre-release`() {
    assertThatVersion("1.0.0-alpha").isLessThanVersion("1.0.0-beta")
  }

  @Test
  fun `numeric segments are compared as numbers not as strings`() {
    assertThatVersion("1.10.0").isGreaterThanVersion("1.9.0")
  }

  @Test
  fun `versions sharing a suffix are compared by their numeric parts`() {
    assertThatVersion("32.1.3-jre").isGreaterThanVersion("31.0.0-jre")
    assertThatVersion("32.1.3-jre").isEqualToVersion("32.1.3-jre")
  }

  @Test
  fun `single segment versions compare by their only number`() {
    assertThatVersion("2").isGreaterThanVersion("1")
    assertThatVersion("5").isEqualToVersion("5")
  }

  @Test
  fun `a pre-release version has lower precedence than its release`() {
    // This directly targets the original bug where "1.0.0-alpha" > "1.0.0"
    assertThatVersion("1.0.0-alpha").isLessThanVersion("1.0.0")
    assertThatVersion("1.0.0").isGreaterThanVersion("1.0.0-alpha")

    // Check with missing segment normalization
    assertThatVersion("1.0-rc1").isLessThanVersion("1.0")
  }

  @Test
  fun `numeric pre-release parts are compared numerically not alphabetically`() {
    // Crucial check: "2" vs "11" inside pre-releases must not use alphabetical sort
    assertThatVersion("1.0.0-rc.2").isLessThanVersion("1.0.0-rc.11")
    assertThatVersion("1.0.0-alpha.11").isGreaterThanVersion("1.0.0-alpha.2")
  }

  @Test
  fun `a numeric pre-release identifier has lower precedence than a non-numeric one`() {
    // SemVer rule: Numeric identifiers < Non-numeric identifiers
    assertThatVersion("1.0.0-1").isLessThanVersion("1.0.0-alpha")
    assertThatVersion("1.0.0-alpha").isGreaterThanVersion("1.0.0-2")
  }

  @Test
  fun `a pre-release with more fields has higher precedence than its prefix`() {
    // SemVer rule: A larger set of pre-release fields has a higher precedence than a smaller set
    assertThatVersion("1.0.0-alpha.1").isGreaterThanVersion("1.0.0-alpha")
    assertThatVersion("1.0.0-rc.1.test").isGreaterThanVersion("1.0.0-rc.1")
  }

  @Test
  fun `pre-release qualifiers are compared field by field in order`() {
    // "alpha" < "beta", despite "1" vs "2" later on
    assertThatVersion("1.0.0-alpha.2").isLessThanVersion("1.0.0-beta.1")
    // "rc" > "beta"
    assertThatVersion("1.0.0-rc.1").isGreaterThanVersion("1.0.0-beta.12")
  }

  @Test
  fun `the main version takes precedence over pre-release qualifiers`() {
    // Checks that main versions are still prioritized over pre-releases even if main lengths differ
    assertThatVersion("1.0.1-alpha").isGreaterThanVersion("1.0.0")
    assertThatVersion("2.0-rc1").isGreaterThanVersion("1.9.9")
  }
}

private fun assertThatVersion(version: String): Assert<Version> = assertThat(Version(version))

private fun Assert<Version>.isGreaterThanVersion(other: String): Assert<Version> = transform { v ->
  assertThat(v.compareTo(Version(other))).isGreaterThan(0)
  v
}

private fun Assert<Version>.isLessThanVersion(other: String): Assert<Version> = transform { v ->
  assertThat(v.compareTo(Version(other))).isLessThan(0)
  v
}

private fun Assert<Version>.isEqualToVersion(other: String): Assert<Version> = transform { v ->
  assertThat(v.compareTo(Version(other))).isZero()
  v
}
