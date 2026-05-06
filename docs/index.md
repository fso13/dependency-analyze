---
layout: default
title: dependency-analyze
---

# dependency-analyze

Gradle plugin that generates a single-page HTML report with:
- full dependency list (including `project(...)` modules)
- detected licenses (from Maven POM metadata)
- vulnerabilities and fix recommendations (via Sonatype OSS Index, optional credentials)
- client-side filters (module / scope / license / search)

## Quick start (example project in this repo)

Generate report:

```bash
./gradlew dependencyAnalyzeReport
```

Generate single aggregated report for all modules (root task):

```bash
./gradlew dependencyAnalyzeAggregate
```

Output (default):
- `build/reports/dependency-analyze/index.html`

## Use Plugin In Another Project

### Kotlin DSL (`settings.gradle.kts` + `build.gradle.kts`)

1) In target project `settings.gradle.kts`, include plugin build:

```kotlin
pluginManagement {
  includeBuild("../dependency-analize/build-logic")
}
```

2) In target project `build.gradle.kts`, apply and configure plugin:

```kotlin
plugins {
  id("io.github.fso13.dependency-analyze")
}

dependencyAnalyze {
  configurationNames.set(setOf("runtimeClasspath", "compileClasspath", "testRuntimeClasspath"))
  includeProjectDependencies.set(true)
  includeTransitives.set(true)

  vulnerabilityProvider.set("ossIndex")
  ossIndexToken.set(providers.environmentVariable("OSSINDEX_TOKEN"))
}
```

3) Run report task:

```bash
./gradlew dependencyAnalyzeReport
```

Or run aggregated report for all modules:

```bash
./gradlew dependencyAnalyzeAggregate
```

### Groovy DSL (`settings.gradle` + `build.gradle`)

1) In target project `settings.gradle`:

```groovy
pluginManagement {
  includeBuild('../dependency-analize/build-logic')
}
```

2) In target project `build.gradle`:

```groovy
plugins {
  id 'io.github.fso13.dependency-analyze'
}

dependencyAnalyze {
  configurationNames = ['runtimeClasspath', 'compileClasspath', 'testRuntimeClasspath'] as Set
  includeProjectDependencies = true
  includeTransitives = true

  vulnerabilityProvider = 'ossIndex'
  ossIndexToken = System.getenv('OSSINDEX_TOKEN')
}
```

3) Run report task:

```bash
./gradlew dependencyAnalyzeReport
```

Or run aggregated report for all modules:

```bash
./gradlew dependencyAnalyzeAggregate
```

## Configuration

In `build.gradle.kts`:

```kotlin
dependencyAnalyze {
  configurationNames.set(setOf("runtimeClasspath", "compileClasspath", "testRuntimeClasspath"))
  includeProjectDependencies.set(true)
  includeTransitives.set(true)

  // Vulnerabilities:
  vulnerabilityProvider.set("ossIndex") // or "none"
  // Bearer token for OSS Index API:
  ossIndexToken.set(providers.environmentVariable("OSSINDEX_TOKEN"))

  // Optional: fail the build when vuln lookup fails (401/network/etc)
  // ignoreVulnerabilityErrors.set(false)
}
```

## Publish To Gradle Plugin Portal

1) Create account and API token on [plugins.gradle.org](https://plugins.gradle.org/).

2) Provide credentials (recommended via environment variables):

```bash
export GRADLE_PUBLISH_KEY="<your key>"
export GRADLE_PUBLISH_SECRET="<your secret>"
```

Or put them into your local `~/.gradle/gradle.properties`:

```properties
gradle.publish.key=<your key>
gradle.publish.secret=<your secret>
```

3) Publish:

```bash
./gradlew :build-logic:dependency-analyze-gradle-plugin:publishPlugins
```
