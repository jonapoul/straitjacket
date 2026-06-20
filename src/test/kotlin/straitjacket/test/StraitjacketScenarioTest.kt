package straitjacket.test

import blueprint.test.ScenarioTest

abstract class StraitjacketScenarioTest : ScenarioTest() {
  override val gradleVersion = GRADLE_VERSION
}
