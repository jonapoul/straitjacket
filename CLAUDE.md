# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Single-module Kotlin JVM Gradle build. The JDK is pinned in `.java-version`, Gradle in
`gradle/wrapper/gradle-wrapper.properties`, everything else in `gradle/libs.versions.toml`.

```bash
./gradlew test                                          # all tests
./gradlew test --tests "straitjacket.CheckScenario"     # one scenario class
./gradlew test --tests "straitjacket.CheckScenario.a directly requested newer version fails the check"
./gradlew detektCheck                                   # static analysis
./gradlew checkKotlinAbi                                # verifies api/straitjacket.api
./gradlew updateKotlinAbi                               # regenerate it after a public API change
./gradlew licensee validatePlugins
./gradlew koverHtmlReport koverVerify                   # coverage, as CI runs it
scripts/ktfmt.sh format                                 # format files changed since main
scripts/ktfmt.sh check --force                          # format check over everything, as CI runs it
```

Don't run `gradle check` yourself, keep test runs as specific as you can.

## Style

`ktfmt --google-style` is the formatting source of truth, not `.editorconfig`. `allWarningsAsErrors`
and `explicitApi()` are on, so any new public declaration needs an explicit visibility modifier and a
matching entry in `api/straitjacket.api`.

ktfmt allows comments at a max width of 100 characters. Break lines if longer than that.

## Architecture

The plugin (`dev.jonpoulton.straitjacket`, entry point `straitjacket.StraitjacketPlugin`) makes
version catalogs binding: it forces dependencies requested *below* their catalog version up, and
fails the build when something resolves *above* it. See README.md for the user-facing behaviour.

The two halves run at different times and this distinction drives the whole design:

- **Forcing** happens during dependency resolution, on every build, via a
  `resolutionStrategy.dependencySubstitution.all {}` rule registered per catalog per resolvable
  configuration (`forceCatalogVersions` in `internal/restrictions.kt`).
- **Checking** happens in `StraitjacketCheck` task actions, one task per catalog plus an aggregate
  `straitjacketCheck` wired into `check` (`internal/tasks.kt`).

`apply` itself only wires those together. Every read of the extension and of a Gradle property is in
`StraitjacketSettings` (`internal/StraitjacketSettings.kt`), which both halves are handed. The
version maps in `internal/catalog.kt` are built once in `apply` and shared, so catalog alias
iteration order can never make the two halves disagree.

`Version` (`internal/Version.kt`) is a hand-rolled SemVer comparator, used by both halves. Build
metadata (`+...`) is not handled.

Configuration cache and isolated projects compatibility are hard requirements (`ConfigCacheScenario`,
`IsolatedProjectsScenario`).

The KDoc carries the reasoning for the parts that look wrong until you know why: the substitution
rule over `eachDependency`, the internal Gradle API `currentTarget()` and `withVersion()` helpers
in `internal/restrictions.kt`, the `Error` thrown by `internal/properties.kt`, and which side can
filter configurations on what. Each names the scenario test that pins it, and nothing else guards
them. Read it before changing any of them.

### Performance invariants

The substitution rule asks "is Straitjacket active?" once per dependency per catalog per resolution
and Gradle does not memoise provider chains, so `StraitjacketSettings.finalizedProperty` wraps
everything that rule reads. `resolvedVersions` (`internal/tasks.kt`) wraps a Kotlin `lazy {}` in a
provider so the resolution graph is walked once for all per-catalog check tasks.
`ResolutionRuleOverheadScenario` and `ResolvedVersionMapOverheadScenario` count evaluations and fail
if either is lost. Don't replace them with simpler-looking provider chains.

## Tests

Bar a couple of plain unit tests over the pure helpers, everything is a Gradle TestKit scenario test
built on blueprint's `ScenarioTest`. One class per scenario, extending `StraitjacketScenarioTest`:

```kotlin
class SomethingScenario : StraitjacketScenarioTest() {
  override val fileTree = fileTree {
    settingsGradleKts()          // defaults to blueprint's repositories block
    buildGradleKts("""...""".trimIndent())
    libsVersionsToml("""...""".trimIndent())
  }

  @Test fun `what it does`() = runScenario {
    assertThatTask(":straitjacketCheck").buildsSuccessfully().taskSucceeded(":straitjacketCheckLibs")
  }
}
```

Helpers live in `src/test/kotlin/straitjacket/test/`: `fileTrees.kt` (file builders),
`assertions.kt` (`trimmedOutputContains`, `tasksSkipped`, `withoutConfigurationCache`,
`withGradleProperty`).

- The harness runs with the configuration cache **on** by default. A fixture that resolves a
  configuration in `doLast` or shares mutable state between configuration and execution must call
  `.withoutConfigurationCache()`.
- Scenarios resolve real artifacts from Maven Central, so tests need network access on a cold cache.
- Assert on observable behaviour, not just task outcome: `ForceUpScenario` registers a fixture task
  that prints the resolved version rather than trusting the check passing.
