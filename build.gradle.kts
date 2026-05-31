@file:OptIn(ExperimentalAbiValidation::class)

import app.cash.licensee.UnusedAction.IGNORE
import blueprint.core.javaVersion
import blueprint.core.jvmTarget
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.report.ReportMergeTask
import org.gradle.api.attributes.plugin.GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  alias(libs.plugins.blueprint.core) apply false

  `java-gradle-plugin`
  alias(libs.plugins.blueprint.test)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dokka)
  alias(libs.plugins.kotlin)
  alias(libs.plugins.licensee)
  alias(libs.plugins.publish)
  alias(libs.plugins.publishReport)
}

dependencies {
  compileOnly(kotlin("gradle-plugin"))
  testCompileOnly(libs.junit.api)
  testImplementation(kotlin("stdlib"))
  testImplementation(kotlin("test"))
  testImplementation(libs.assertk)
  testImplementation(libs.blueprintAssertk)
  testPluginClasspath(kotlin("gradle-plugin"))
  testRuntimeOnly(libs.junit.launcher)
}

gradlePlugin {
  vcsUrl = "https://github.com/jonapoul/straitjacket.git"
  website = "https://github.com/jonapoul/straitjacket"

  plugins {
    register("straitjacket") {
      id = "dev.jonpoulton.straitjacket"
      description = providers.gradleProperty("POM_DESCRIPTION").get()
      implementationClass = "straitjacket.StraitjacketPlugin"
      displayName = "Straitjacket"
      tags.addAll("straitjacket", "dependency", "restriction")
    }
  }
}

tasks.validatePlugins {
  enableStricterValidation = true
  failOnWarning = true
}

kotlin {
  abiValidation { enabled = true }

  compilerOptions {
    allWarningsAsErrors = true
    jvmTarget = jvmTarget()
    explicitApi()
  }
}

val javaVersion = javaVersion()

java {
  sourceCompatibility = javaVersion.get()
  targetCompatibility = javaVersion.get()
}

licensee {
  unusedAction(IGNORE)
  listOf("Apache-2.0", "MIT").forEach(::allow)
  allowUrl("https://www.eclipse.org/legal/epl-v20.html")
}

detekt {
  config.from(file("config/detekt.yml"))
  buildUponDefaultConfig = true
  allRules = true
  parallel = true
}

val detektTasks = tasks.withType(Detekt::class)
val detektCheck by tasks.registering { dependsOn(detektTasks) }

val detektReportMergeSarif =
  tasks.register("detektReportMergeSarif", ReportMergeTask::class) {
    output = layout.buildDirectory.file("reports/detekt/merge.sarif.json")
  }

detektReportMergeSarif.configure { input.from(detektTasks.map { it.reports.sarif.outputLocation }) }

tasks.check.configure { dependsOn(detektReportMergeSarif) }

detektTasks.configureEach {
  reports {
    html.required = true
    sarif.required = true
    checkstyle.required = false
    markdown.required = false
  }

  exclude { node ->
    !node.isDirectory && node.file.absolutePath.contains("generated", ignoreCase = true)
  }

  finalizedBy(detektReportMergeSarif)
}

configurations.named("apiElements") {
  attributes {
    attribute(GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named<GradlePluginApiVersion>("8.13"))
  }
}

buildConfig {
  generateAtSync = true
  sourceSets.named("test") {
    packageName.set("straitjacket.test")
    useKotlinOutput { topLevelConstants = true }
    buildConfigField("GRADLE_VERSION", GradleVersion.current().version)
  }
}

tasks.withType(Test::class).configureEach {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
    exceptionFormat = FULL
    showCauses = true
    showExceptions = true
    showStackTraces = true
    showStandardStreams = false
    displayGranularity = 2
  }
}
