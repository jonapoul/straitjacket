# Straitjacket Gradle Plugin

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://github.com/jonapoul/straitjacket/releases"><img alt="Release" src="https://img.shields.io/github/v/release/jonapoul/straitjacket"/></a>
  <a href="https://github.com/jonapoul/straitjacket"><img alt="Coverage" src="https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/jonapoul/995f996e31d24ad523bde9f758307d9a/raw/straightjacket-coverage.json"/></a>
</p>

## The Problem

Let's say you have a multi-module Gradle project, with a `libs.versions.toml` file like:

```toml
[libraries]
foo = { module = "com.website:foo", version = "1.2.3" }
bar = { module = "com.website:bar", version = "2.3.4" }
```

and a module structure like:

```
:app
├── :module-a
│   └── com.website:foo:1.2.3
│       └── com.website:bar:2.3.4
└── :module-b
    └── com.website:bar:2.3.4
```

Everything lines up with the catalog. Then `foo` puts out a new version 1.3.0 and you update it:

```diff
-foo = { module = "com.website:foo", version = "1.2.3" }
+foo = { module = "com.website:foo", version = "1.3.0" }
```

What you didn't notice is that `foo` 1.3.0 now depends on `bar` 3.4.5. Gradle's default conflict resolution picks the highest requested version across the whole graph, so this is what `:app` actually resolves:

```
:app
├── :module-a
│   └── com.website:foo:1.3.0
│       └── com.website:bar:3.4.5        <-- catalog says 2.3.4
└── :module-b
    └── com.website:bar:2.3.4 -> 3.4.5   <-- and it drags :module-b up with it
```

But if you build `:module-b` on its own (e.g. unit tests), nothing in its graph asks for anything higher, so it resolves exactly what it declared:

```
:module-b
└── com.website:bar:2.3.4
```

Nothing failed and nothing warned you, but two things are now wrong. Your catalog claims `bar` is on 2.3.4 when the thing you ship is running 3.4.5, so the file everyone treats as the source of truth has been superseded. And `:module-b` is now compiled and unit tested against a different version of `bar` than the one it runs against in production. Any test in `:module-b` that asserts 2.3.4 behaviour still passes, and tells you nothing about what actually ships.

It happens in the other direction too, though it needs one more thing to go wrong. As long as some module declares `bar` from the catalog, the same highest-wins rule handles a transitive request below 2.3.4 by itself. Nothing guarantees the catalog version is in the graph at all, though: if `bar` only ever turns up transitively, or a module hardcoded an older version string instead of using the alias, the older version is what you compile and ship.

None of this is Gradle misbehaving. Version declarations are requests rather than commitments, and conflict resolution settles the difference without asking. That's usually what you want. It's less useful when you've gone to the effort of writing down a version in one place and expect that to be the version you get.

## The Solution

Straitjacket makes the catalog binding. Once applied, for every resolvable configuration and every registered [Gradle version catalog](https://docs.gradle.org/current/userguide/version_catalogs.html) it:

- **Forces up** any dependency requested *below* its catalog version. If something (yours or a transitive dependency) asks for an older version than the catalog declares, it gets bumped, with Straitjacket recorded as the selection reason.
- **Fails the build** if a dependency resolves to a version *newer* than the catalog declares, for example because a transitive dependency dragged it up. You find out about the drift straight away rather than letting the catalog quietly fall out of date.

So the catalog version becomes an exact pin instead of a "minimum", and you hear about it as soon as reality disagrees. In the example above, the build stops the moment `foo` 1.3.0 drags `bar` up to 3.4.5, and you decide what to do about it: bump the catalog to 3.4.5 deliberately, or hold `foo` back.

Note that the two halves happen at different times. Forcing up is part of dependency resolution, so it applies to every build. The newer-than-catalog check only runs when you run the check tasks below.

## Usage

Apply the plugin to any project that resolves dependencies:

```kotlin
// build.gradle.kts
plugins {
  id("dev.jonpoulton.straitjacket") version "<version>"
}
```

That's it. Out of the box, Straitjacket covers every resolvable configuration and every version catalog registered in the build. Forcing happens automatically while Gradle resolves dependencies, and the checks run as tasks.

### Tasks

| Task | Description |
| --- | --- |
| `straitjacketCheck` | Aggregate task that runs every per-catalog check. |
| `straitjacketCheck<Catalog>` | One task per catalog, e.g. `straitjacketCheckLibs` for the default `libs.versions.toml`. Fails if any dependency resolved newer than that catalog declares. |

The `straitjacketCheck` task hooks into the standard `check` lifecycle task, so it runs as part of your normal build and CI verification. That wiring needs the `base` plugin, which you get for free from any of the usual language plugins such as `kotlin("jvm")`.

Each per-catalog check writes a report to `build/reports/straitjacket/<catalog>.txt`. It's empty if the check passed, and lists the offending coordinates if it didn't:

```
Straitjacket found dependencies resolved to versions newer than the version catalog declares:

  com.website:bar:2.3.4 -> 3.4.5 (in compileClasspath, runtimeClasspath)

Update your version catalog, or exclude them with ignoredModules or ignoredConfigurations.
```

### Configuration

Configure the plugin through the `straitjacket` extension:

```kotlin
straitjacket {
  // Set to false to turn off both the forcing and the checks. Defaults to true.
  enabled = true

  // Set to false to have the checks log their report and succeed, rather than fail the
  // build. Defaults to true.
  failOnViolation = true

  // The level to log every dependency that gets forced up at. Unset by default, which
  // is silent.
  logForcedVersions = LogLevel.LIFECYCLE

  // Exclude configurations (by name) from both forcing and checking. "*" matches any run
  // of characters, so a single pattern can cover a whole family of generated names.
  ignoredConfigurations.add("someConfiguration")
  ignoredConfigurations.add("*UnitTestRuntimeClasspath")

  // Exclude individual modules from both forcing and checking. "*" matches any run of
  // characters, so this covers one module, a whole group, or a name in any group.
  ignoredModules.add("com.website:bar")
  ignoredModules.add("com.website:*")
  ignoredModules.add("*:bar")

  // Exclude whole catalogs when more than one is registered. The name is the catalog's
  // accessor name, e.g. "someOtherLibs" for a "someOtherLibs.versions.toml" file.
  ignoredCatalogs.add("someOtherLibs")
}
```

A configuration name without a `*` matches exactly, so patterns only ever widen what an entry covers. They're worth reaching for when the configurations you mean are generated per variant, as on Android, where `debugUnitTestRuntimeClasspath`, `releaseUnitTestRuntimeClasspath` and the rest would otherwise be a list to keep in sync with your variants.

`ignoredModules` is usually the one you want when a single dependency is the problem. If a transitive drags `com.website:bar` above the catalog and you can't fix it today, ignoring the configuration it turned up in gives up on every other module that configuration resolves too. An ignored module is one Straitjacket stops managing altogether, so it isn't forced up either and resolves exactly as it would without the plugin. Everything outside a `*` is matched literally, so the dots in a group name aren't wildcards.

`failOnViolation = false` is for adopting Straitjacket on a project that isn't clean yet, where failing on day one isn't an option but the drift still wants watching. The check tasks log the same report at `WARN` and succeed. Note that a task which succeeds is up to date on the next build and doesn't log its warning again, so treat the report file as the record. It's written either way, and the warning says where it is.

Forcing is otherwise silent: it leaves no trace beyond the selection reason, which you only see by asking for a dependency insight report. Setting `logForcedVersions` to a [`LogLevel`](https://docs.gradle.org/current/javadoc/org/gradle/api/logging/LogLevel.html) says what moved and where, one line per configuration that resolved the dependency:

```
Straitjacket forced com.website:bar 2.3.4 -> 3.4.5 in runtimeClasspath (catalog 'libs')
```

Which level to pick depends on what you want it for. `INFO` keeps it out of a normal build and puts it behind `--info`, for when you're working something out and don't want to keep switching it on and off. `LIFECYCLE` or higher makes it a permanent part of the build log, for a record in CI. Leave it unset for silence, or call `logForcedVersions.unset()` to opt out of a level a convention plugin set.

The logging happens during resolution, so a build that resolves nothing, having found its tasks up to date, logs nothing.

`enabled`, `failOnViolation` and `logForcedVersions` can each also be set with a Gradle property of the same name under a `straitjacket.` prefix. Each wins over its extension value, so you can change any of them for a one-off build without touching the build script:

```
./gradlew build -Pstraitjacket.enabled=false
./gradlew check -Pstraitjacket.failOnViolation=false
./gradlew build -Pstraitjacket.logForcedVersions=info
```

A level name is matched without regard to case. A value that names no level, or that isn't `true` or `false` for the two boolean ones, fails the build rather than quietly falling back to the extension value, so a typo can't leave the build doing the opposite of what you asked for. Each is checked when it's read, and `logForcedVersions` is only read while forcing, so a build that resolves nothing at all is the one that won't tell you.

Put them in `gradle.properties` (project or `~/.gradle`) if you want that to be the default for a given machine or environment.

### Notes

- Only **resolvable** configurations are looked at, meaning ones Gradle can resolve to a concrete set of artifacts. `implementation` and `api` get covered via the classpath configurations they feed into.
- Versions are compared using [Semantic Versioning](https://semver.org/) precedence, including pre-release ordering like `1.0.0-alpha` < `1.0.0`. Build metadata (`+...`) isn't stripped out and can compare wrongly, so don't rely on it in catalog versions.
- If the same module is declared more than once at different versions, whether under two aliases in one catalog or by two different catalogs, the highest declared version is the one that counts. It's what gets forced, and no check reports it as a violation. Ignored catalogs don't get a say.
- The plugin is compatible with Gradle's [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html).
- Conceptually this is fairly similar to [Dependency Guard](https://github.com/dropbox/dependency-guard) - although Straitjacket is more aggressive in its enforcement and doesn't generate a classpath file. They should be okay to work alongside each other.

## License

```
Copyright (C) 2026 Jon Poulton

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
