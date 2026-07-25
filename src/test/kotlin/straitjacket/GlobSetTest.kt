package straitjacket

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import straitjacket.internal.GlobSet

class GlobSetTest {
  @Test
  fun `an empty set matches nothing`() {
    assertThatGlobSet().doesNotMatch("com.website:foo").doesNotMatch("")
  }

  @Test
  fun `a literal pattern matches only itself`() {
    assertThatGlobSet("com.website:foo")
      .matches("com.website:foo")
      .doesNotMatch("com.website:bar")
      .doesNotMatch("com.other:foo")
  }

  @Test
  fun `a literal pattern does not match a substring or a superstring`() {
    assertThatGlobSet("com.website:foo")
      .doesNotMatch("com.website:foobar")
      .doesNotMatch("website:foo")

    assertThatGlobSet("com.website:foobar").doesNotMatch("com.website:foo")
  }

  @Test
  fun `any of several patterns can match`() {
    assertThatGlobSet("com.website:foo", "com.other:bar")
      .matches("com.website:foo")
      .matches("com.other:bar")
      .doesNotMatch("com.website:bar")
  }

  @Test
  fun `a trailing wildcard matches every module in a group`() {
    assertThatGlobSet("com.website:*")
      .matches("com.website:foo")
      .matches("com.website:bar")
      .doesNotMatch("com.other:foo")
  }

  @Test
  fun `a leading wildcard matches the module whatever its group`() {
    assertThatGlobSet("*:foo")
      .matches("com.website:foo")
      .matches("com.other:foo")
      .doesNotMatch("com.website:bar")
  }

  @Test
  fun `a wildcard matches an empty run of characters`() {
    assertThatGlobSet("com.website:*").matches("com.website:")
    assertThatGlobSet("*foo").matches("foo")
  }

  @Test
  fun `a bare wildcard matches everything`() {
    assertThatGlobSet("*").matches("com.website:foo").matches("")
  }

  @Test
  fun `a wildcard in the middle matches across the separator`() {
    assertThatGlobSet("com.*:foo")
      .matches("com.website:foo")
      .matches("com.:foo")
      .doesNotMatch("org.website:foo")
  }

  @Test
  fun `several wildcards in one pattern all apply`() {
    assertThatGlobSet("*.website:*")
      .matches("com.website:foo")
      .matches("org.website:bar")
      .doesNotMatch("com.other:foo")
  }

  @Test
  fun `literal and wildcard patterns work in the same set`() {
    assertThatGlobSet("com.website:foo", "com.other:*")
      .matches("com.website:foo")
      .matches("com.other:anything")
      .doesNotMatch("com.website:bar")
  }

  /** A pattern is not a regex, so anything a regex would read as a metacharacter is a literal. */
  @Test
  fun `a dot in a pattern is matched literally`() {
    assertThatGlobSet("com.website:foo").doesNotMatch("comXwebsite:foo")
  }

  @Test
  fun `regex metacharacters in a pattern are matched literally`() {
    assertThatGlobSet("com.website:foo+")
      .matches("com.website:foo+")
      .doesNotMatch("com.website:fooo")
    assertThatGlobSet("com.website:(foo)").matches("com.website:(foo)")
    assertThatGlobSet("com.website:foo?").doesNotMatch("com.website:fo")
    assertThatGlobSet("com.website:^foo$").matches("com.website:^foo$")
  }

  @Test
  fun `regex metacharacters either side of a wildcard are matched literally`() {
    assertThatGlobSet("com.website:foo[*]")
      .matches("com.website:foo[bar]")
      .doesNotMatch("com.website:foob")
  }

  @Test
  fun `a pattern must match the whole value, not part of it`() {
    assertThatGlobSet("website").doesNotMatch("com.website:foo")
    assertThatGlobSet("com.website:*").doesNotMatch("prefix com.website:foo")
    assertThatGlobSet("*:foo").doesNotMatch("com.website:foo suffix")
  }
}

private fun assertThatGlobSet(vararg patterns: String): Assert<GlobSet> =
  assertThat(GlobSet(patterns.toSet()))

private fun Assert<GlobSet>.matches(value: String): Assert<GlobSet> = transform { globSet ->
  assertThat(value in globSet, name = "\"$value\" matches").isTrue()
  globSet
}

private fun Assert<GlobSet>.doesNotMatch(value: String): Assert<GlobSet> = transform { globSet ->
  assertThat(value in globSet, name = "\"$value\" matches").isFalse()
  globSet
}
