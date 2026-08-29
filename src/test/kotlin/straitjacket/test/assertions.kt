package straitjacket.test

import assertk.Assert
import assertk.assertions.contains
import assertk.assertions.doesNotContain

fun Assert<String>.contains(expected: String): Assert<String> = transform { actual ->
  assertThat(actual).contains(expected)
  actual
}

fun Assert<String>.doesNotContain(expected: String): Assert<String> = transform { actual ->
  assertThat(actual).doesNotContain(expected)
  actual
}
