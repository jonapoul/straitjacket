package straitjacket.gradle

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isZero
import kotlin.test.Test
import straitjacket.gradle.internal.Version

class VersionTest {
  @Test
  fun `equal versions`() {
    assertThatVersion("1.2.3").isEqualToVersion("1.2.3")
  }

  @Test
  fun `higher major`() {
    assertThatVersion("2.0.0").isGreaterThanVersion("1.0.0")
  }

  @Test
  fun `lower major`() {
    assertThatVersion("1.0.0").isLessThanVersion("2.0.0")
  }

  @Test
  fun `higher minor`() {
    assertThatVersion("1.3.0").isGreaterThanVersion("1.2.0")
  }

  @Test
  fun `higher patch`() {
    assertThatVersion("1.2.4").isGreaterThanVersion("1.2.3")
  }

  @Test
  fun `different segment counts`() {
    assertThatVersion("1.2.3.1").isGreaterThanVersion("1.2.3")
    assertThatVersion("1.2.3").isLessThanVersion("1.2.3.1")
  }

  @Test
  fun `missing segments treated as zero`() {
    assertThatVersion("1.2.0").isEqualToVersion("1.2")
  }

  @Test
  fun `hyphenated pre-release segments`() {
    assertThatVersion("1.0.0-alpha").isLessThanVersion("1.0.0-beta")
  }

  @Test
  fun `numeric beats string comparison when both numeric`() {
    // 10 > 9 numerically, but "10" < "9" lexicographically
    assertThatVersion("1.10.0").isGreaterThanVersion("1.9.0")
  }

  @Test
  fun `jre suffix`() {
    assertThatVersion("32.1.3-jre").isGreaterThanVersion("31.0.0-jre")
    assertThatVersion("32.1.3-jre").isEqualToVersion("32.1.3-jre")
  }

  @Test
  fun `single segment versions`() {
    assertThatVersion("2").isGreaterThanVersion("1")
    assertThatVersion("5").isEqualToVersion("5")
  }
}

private fun assertThatVersion(version: String): Assert<Version> = assertThat(Version(version))

private fun Assert<Version>.isGreaterThanVersion(other: String): Assert<Version> =
  transform { it.also { v -> assertThat(v.compareTo(Version(other))).isGreaterThan(0) } }

private fun Assert<Version>.isLessThanVersion(other: String): Assert<Version> =
  transform { it.also { v -> assertThat(v.compareTo(Version(other))).isLessThan(0) } }

private fun Assert<Version>.isEqualToVersion(other: String): Assert<Version> =
  transform { it.also { v -> assertThat(v.compareTo(Version(other))).isZero() } }
