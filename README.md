# Straitjacket Gradle Plugin

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://github.com/jonapoul/straitjacket/releases"><img alt="Release" src="https://img.shields.io/github/v/release/jonapoul/straitjacket"/></a>
  <a href="https://github.com/jonapoul/straitjacket"><img alt="Coverage" src="https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/jonapoul/995f996e31d24ad523bde9f758307d9a/raw/straightjacket-coverage.json"/></a>
</p>

## The Problem

You have a multi-module Gradle project, with a `libs.versions.toml` dependency tracker file like:

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

Everything lines up with the catalog. Then `foo` puts out a 1.3.0 and you bump it:

```toml
foo = { module = "com.website:foo", version = "1.3.0" }
```

What you didn't notice is that `foo` 1.3.0 now depends on `bar` 2.5.0. Gradle's default conflict resolution picks the highest requested version across the whole graph, so this is what `:app` actually resolves:

```
:app
├── :module-a
│   └── com.website:foo:1.3.0
│       └── com.website:bar:2.5.0        <-- catalog says 2.3.4
└── :module-b
    └── com.website:bar:2.3.4 -> 2.5.0   <-- and it drags :module-b up with it
```

Build `:module-b` on its own, though, and nothing in its graph asks for anything higher, so it resolves exactly what it declared:

```
:module-b
└── com.website:bar:2.3.4
```

Nothing failed and nothing warned you, but two things are now wrong. Your catalog claims `bar` is on 2.3.4 when the thing you ship is running 2.5.0, so the file everyone treats as the source of truth has been superseded. And `:module-b` is now compiled and unit tested against a different version of `bar` than the one it runs against in production. Any test in `:module-b` that asserts 2.3.4 behaviour still passes, and tells you nothing about what actually ships.

It happens in the other direction too. An old transitive dependency can request a version *below* what the catalog declares, and if nothing else in the graph pulls it back up, you quietly ship the older one.

None of this is Gradle misbehaving. Version declarations are requests rather than commitments, and conflict resolution settles the difference without asking. That's usually what you want. It's less useful when you've gone to the effort of writing down a version in one place and expect that to be the version you get.

## The Solution

Straitjacket makes the catalog binding. Once applied, for every resolvable configuration and every registered [Gradle version catalog](https://docs.gradle.org/current/userguide/version_catalogs.html) it:

- **Forces up** any dependency requested *below* its catalog version. If something (yours or a transitive dependency) asks for an older version than the catalog declares, it gets bumped, with Straitjacket recorded as the selection reason.
- **Fails the build** if a dependency resolves to a version *newer* than the catalog declares, for example because a transitive dependency dragged it up. You find out about the drift straight away rather than letting the catalog quietly fall out of date.

So the catalog version becomes an exact pin instead of a "minimum", and you hear about it as soon as reality disagrees. In the example above, the build stops the moment `foo` 1.3.0 drags `bar` up to 2.5.0, and you decide what to do about it: bump the catalog to 2.5.0 deliberately, or hold `foo` back.

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
| `straitjacketCheck<Catalog>` | One task per catalog, e.g. `straitjacketCheckLibs` for `libs.versions.toml`. Fails if any dependency resolved newer than that catalog declares. |

The `straitjacketCheck` task hooks into the standard `check` lifecycle task, so it runs as part of your normal build and CI verification. That wiring needs the `base` plugin, which you get for free from any of the usual language plugins such as `kotlin("jvm")`.

Each per-catalog check writes a report to `build/reports/straitjacket/<catalog>.txt`. It's empty if the check passed, and lists the offending coordinates if it didn't:

```
Straitjacket found dependencies resolved to versions newer than the version catalog declares:

  com.website:bar:2.3.4 -> 2.5.0 (in compileClasspath, runtimeClasspath)

Update your version catalog or add these configurations to ignoredConfigurations.
```

### Configuration

Configure the plugin through the `straitjacket` extension:

```kotlin
straitjacket {
  // Set to false to turn off both the forcing and the checks. Defaults to true.
  enabled = true

  // Exclude configurations (by name) from both forcing and checking.
  ignoredConfigurations.add("someConfiguration")

  // Exclude whole catalogs when more than one is registered. The name is the catalog's
  // accessor name, e.g. "someOtherLibs" for a "someOtherLibs.versions.toml" file.
  ignoredCatalogs.add("someOtherLibs")
}
```

You can also flip it on or off with the `straitjacket.enabled` Gradle property. It wins over the `enabled` extension value, so you can skip a one-off build without touching the build script:

```
./gradlew build -Pstraitjacket.enabled=false
```

Put it in `gradle.properties` (project or `~/.gradle`) if you want that to be the default for a given machine or environment.

### Notes

- Only **resolvable** configurations are looked at, meaning ones Gradle can resolve to a concrete set of artifacts. `implementation` and `api` get covered via the classpath configurations they feed into.
- Versions are compared using [Semantic Versioning](https://semver.org/) precedence, including pre-release ordering like `1.0.0-alpha` < `1.0.0`. Build metadata (`+...`) isn't stripped out and can compare wrongly, so don't rely on it in catalog versions.
- The plugin is compatible with Gradle's [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html).

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
