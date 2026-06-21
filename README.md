# Straitjacket - Gradle Plugin

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://github.com/jonapoul/straitjacket/releases"><img alt="Release" src="https://img.shields.io/github/v/release/jonapoul/straitjacket"/></a>
  <a href="https://github.com/jonapoul/straitjacket"><img alt="Coverage" src="https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/jonapoul/995f996e31d24ad523bde9f758307d9a/raw/straightjacket-coverage.json"/></a>
</p>

## Introduction

Straitjacket keeps the dependency versions your build actually resolves in lockstep with the versions
declared in your [Gradle version catalog(s)][catalogs]. Your `libs.versions.toml` already records the
version you *intend* to use for each library — Straitjacket makes Gradle honour it.

Once applied, for every resolvable configuration and every registered version catalog it:

- **Forces up** any dependency requested *below* its catalog version to the catalog version. If
  something (yours or a transitive dependency) asks for an older version than the catalog declares,
  it is bumped up, with Straitjacket recorded as the selection reason.
- **Fails the build** if a dependency resolves to a version *newer* than the catalog declares — for
  example because a transitive dependency dragged it up. This surfaces drift the moment it appears,
  so the catalog stays the single source of truth instead of silently falling behind.

The net effect: the catalog version is treated as an exact pin rather than a "minimum", and you find
out immediately when reality diverges from it.

## Usage

Apply the plugin to any project that resolves dependencies:

```kotlin
// build.gradle.kts
plugins {
  id("dev.jonpoulton.straitjacket") version "<version>"
}
```

With no further configuration, Straitjacket acts on every resolvable configuration and every version
catalog registered in the build. The forcing behaviour applies automatically during dependency
resolution; the verification behaviour is exposed through check tasks.

### Tasks

| Task | Description |
| --- | --- |
| `straitjacketCheck` | Aggregate task that runs every per-catalog check. |
| `straitjacketCheck<Catalog>` | One task per catalog, e.g. `straitjacketCheckLibs` for `libs.versions.toml`. Fails if any dependency resolved newer than that catalog declares. |

When the [`base` plugin][base] is applied (directly, or by most language plugins such as
`kotlin("jvm")`), `straitjacketCheck` is wired into the standard `check` lifecycle task, so it runs
as part of your normal build and CI verification.

Each per-catalog check writes a report to `build/reports/straitjacket/<catalog>.txt`. The file is
empty when the check passes and lists the offending coordinates when it fails:

```
Straitjacket found dependencies resolved to versions newer than the version catalog declares:

  com.squareup.okio:okio:3.16.0 -> 3.16.4 (in compileClasspath, runtimeClasspath)

Update your version catalog or add these configurations to ignoredConfigurations.
```

### Configuration

Configure the plugin through the `straitjacket` extension:

```kotlin
straitjacket {
  // Disable both the forcing and the checks entirely. Defaults to true.
  enabled = true

  // Exclude configurations (by name) from both forcing and checking.
  ignoredConfigurations.add("someConfiguration")

  // Exclude whole catalogs when more than one is registered. The name is the catalog's
  // accessor name, e.g. "someOtherLibs" for a "someOtherLibs.versions.toml" file.
  ignoredCatalogs.add("someOtherLibs")
}
```

To toggle Straitjacket from the command line or per environment, set the `straitjacket.enabled`
Gradle property. It takes priority over the `enabled` extension value, so you can disable a one-off
build without editing the build script:

```
./gradlew build -Pstraitjacket.enabled=false
```

The property can also live in `gradle.properties` (project or `~/.gradle`) for a more permanent
per-environment default.

### Notes

- Only **resolvable** configurations are considered (those Gradle can resolve to a concrete set of
  artifacts), so configurations such as `implementation` and `api` are checked via the classpath
  configurations they feed into.
- Version comparison follows [Semantic Versioning][semver] precedence (including pre-release
  ordering such as `1.0.0-alpha` < `1.0.0`). Build metadata (`+...`) is not stripped and may
  compare incorrectly, so avoid relying on it in catalog versions.
- The plugin is compatible with Gradle's [configuration cache][cc].

[base]: https://docs.gradle.org/current/userguide/base_plugin.html
[catalogs]: https://docs.gradle.org/current/userguide/version_catalogs.html
[cc]: https://docs.gradle.org/current/userguide/configuration_cache.html
[semver]: https://semver.org/

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
