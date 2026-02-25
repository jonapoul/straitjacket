@file:Suppress("UnstableApiUsage")

rootProject.name = "straitjacket"

pluginManagement {
  repositories {
    google {
      mavenContent {
        includeGroupByRegex(".*android.*")
        includeGroupByRegex(".*google.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupByRegex(".*android.*")
        includeGroupByRegex(".*google.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
  id("com.gradle.develocity") version "4.3.2"
  id("org.jetbrains.kotlinx.kover.aggregation") version "0.9.7"
}

develocity.buildScan {
  if (!gradle.startParameter.isBuildScan) {
    publishing.onlyIf { it.isAuthenticated }
  }

  uploadInBackground = false
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
