---
layout: default
title: dependency-analyze
---

# Dependency Analyze Gradle Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fso13.dependency-analyze)](https://plugins.gradle.org/plugin/io.github.fso13.dependency-analyze)

A Gradle plugin that produces an **interactive HTML report** of your project’s dependencies: resolved coordinates, **direct vs transitive** usage, **license** metadata from Maven POMs, **latest published versions** (from `maven-metadata.xml`), and **security findings** via [Sonatype OSS Index](https://ossindex.sonatype.org/).

**Russian documentation:** [README.ru.md](README.ru.md)

---

## What problem does this solve?

- **Inventory:** See every external and `project(...)` dependency per Gradle module and configuration.
- **Upgrade planning:** Compare the resolved version with the **latest** version available from your configured repositories.
- **Compliance:** Surface **licenses** declared in POMs (including parent POM chain) with optional links.
- **Security (SCA):** Query OSS Index for known issues; explore details in the report (severity, CVSS, titles, references).
- **Multi-module:** Run a per-module report, or a **single aggregated** report for the whole build.

The report runs in the browser with filters and search (module, configuration, license, text search). You can **export** slices of the data (for example vulnerability tables) from the UI where supported.

---

## Requirements

- **Gradle:** A recent Gradle version is recommended (this repository is validated with the wrapper version in `gradle/wrapper/gradle-wrapper.properties`).
- **JVM for the plugin:** The plugin is built for **Java 17+** toolchains.
- **Network:** Resolving latest versions, POMs for licenses, and OSS Index lookups require outbound HTTPS to your artifact repositories and Sonatype (unless you disable vulnerability scanning).

---

## Applying the plugin

### Plugins DSL (Gradle Plugin Portal)

Use the canonical plugin id **`io.github.fso13.dependency-analyze`**. A compatibility alias **`io.github.fso13.dependency-analyze-gradle-plugin`** points to the same implementation.

**`settings.gradle.kts`**

```kotlin
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}
```

**`build.gradle.kts`**

```kotlin
plugins {
  id("io.github.fso13.dependency-analyze") version "<published-version>"
}
```

Replace `<published-version>` with the version shown on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.fso13.dependency-analyze) for this plugin.

**`build.gradle` (Groovy)**

```groovy
plugins {
  id 'io.github.fso13.dependency-analyze' version '<published-version>'
}
```

### Subprojects

Apply the plugin in the root and each subproject that should appear in reports, or use `subprojects { ... }` / `allprojects { ... }` in the root build script (see the [Configuration](#configuration) example in this repository’s root `build.gradle.kts`).

---

## Usage

### Tasks

| Task | When to use |
|------|-------------|
| **`dependencyAnalyzeReport`** | Generates one HTML report for the **current project** (module). |
| **`dependencyAnalyzeAggregate`** (registered on the **root** project) | Runs `dependencyAnalyzeReport` in every project that has it, then **merges** entries into one HTML file at the root `build` directory. |

Run from the project root:

```bash
./gradlew dependencyAnalyzeReport
```

Aggregated report:

```bash
./gradlew dependencyAnalyzeAggregate
```

### Default output location

By default, each `dependencyAnalyzeReport` writes:

`build/reports/dependency-analyze/index.html`

The aggregate task writes to the **root** project’s:

`build/reports/dependency-analyze/index.html`

Open the HTML file in a browser. Paths are relative to each project’s directory.

The **`dependencyAnalyze`** extension applies to **`dependencyAnalyzeReport`** in each project. The root **`dependencyAnalyzeAggregate`** task merges HTML produced by those reports; its merged document uses built-in defaults for title and policy link (see plugin sources if you need to customize aggregate output).

---

## Screenshots

**Dependency overview** — module, configuration, coordinates, latest version, relation (direct / transitive), licenses, and vulnerability summary.

![Dependency report overview](docs/images/report-overview.png)

**Vulnerability detail** — filter by severity, search, and inspect CVE-style identifiers, CVSS, descriptions, and references (data source: OSS Index / Sonatype).

![Vulnerability detail view](docs/images/report-vulnerabilities.png)

---

## Configuration

The plugin exposes an extension named **`dependencyAnalyze`**.

### Kotlin DSL (`build.gradle.kts`)

```kotlin
dependencyAnalyze {
  // Limit which Gradle configurations are analyzed (empty = all resolvable configurations).
  configurationNames.set(setOf("runtimeClasspath", "compileClasspath", "testRuntimeClasspath"))

  includeProjectDependencies.set(true)
  includeTransitives.set(true)

  outputTitle.set("Dependency Analyze Report")

  // Optional: custom output file (per project).
  // outputFile.set(layout.buildDirectory.file("reports/my-report.html"))

  vulnerabilityProvider.set("ossIndex") // or "none"
  ossIndexToken.set(providers.environmentVariable("OSSINDEX_TOKEN"))

  // When true, vulnerability lookup failures (e.g. 401, network) log a warning instead of failing the task.
  ignoreVulnerabilityErrors.set(true)

  // Optional: link shown in the report header (e.g. internal open-source policy).
  // policyUri.set("https://example.com/oss-policy")
}
```

### Groovy DSL (`build.gradle`)

```groovy
dependencyAnalyze {
  configurationNames = ['runtimeClasspath', 'compileClasspath', 'testRuntimeClasspath'] as Set
  includeProjectDependencies = true
  includeTransitives = true
  outputTitle = 'Dependency Analyze Report'

  vulnerabilityProvider = 'ossIndex'
  ossIndexToken = System.getenv('OSSINDEX_TOKEN') ?: ''

  ignoreVulnerabilityErrors = true
}
```

### Extension properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `configurationNames` | `Set<String>` | empty | If **empty**, all **resolvable** configurations are included. Otherwise only the named configurations. |
| `includeProjectDependencies` | `boolean` | `true` | Include `project(...)` dependencies in the report. |
| `includeTransitives` | `boolean` | `true` | If `true`, include the full resolved external graph; if `false`, only first-level external dependencies. |
| `outputTitle` | `String` | `"Dependency report"` | HTML document title / heading. |
| `outputFile` | `RegularFile` | `build/reports/dependency-analyze/index.html` | Output HTML path (convention set by the plugin). |
| `vulnerabilityProvider` | `String` | `"ossIndex"` | `"ossIndex"` for Sonatype OSS Index, or `"none"` to skip vulnerability requests. |
| `ossIndexToken` | `String` | _unset_ | Bearer token for OSS Index. Without a token, the API may return **401** and vulnerability sections may be empty. |
| `ignoreVulnerabilityErrors` | `boolean` | `true` | If `true`, swallow vulnerability resolver errors; if `false`, fail the task on error. |
| `policyUri` | `String` | _optional_ | Optional URI string shown in the report header. |

Repositories declared on any subproject (`repositories { mavenCentral(); ... }`) are used to fetch POMs and `maven-metadata.xml`, including **Basic** auth when `PasswordCredentials` are configured on a `MavenArtifactRepository`.

---

## OSS Index token (recommended for vulnerability data)

1. Create a free account and token at [https://ossindex.sonatype.org/](https://ossindex.sonatype.org/) (see Sonatype’s documentation for current steps).
2. Provide the token to Gradle, for example:

```bash
export OSSINDEX_TOKEN="<your-token>"
```

Or store a secret in `~/.gradle/gradle.properties` (do **not** commit this file to Git) and read it from the build script:

```properties
OSSINDEX_TOKEN=<your-token>
```

```kotlin
ossIndexToken.set(providers.gradleProperty("OSSINDEX_TOKEN"))
```

You can combine `environmentVariable`, `gradleProperty`, and your own logic (for example a `.env` file) in one `Provider` chain if needed.

---

## Local development (`includeBuild`)

To try the plugin from a checkout of this repository without publishing:

**`settings.gradle.kts`**

```kotlin
pluginManagement {
  includeBuild("/path/to/dependency-analize/build-logic")
}
```

**`build.gradle.kts`**

```kotlin
plugins {
  id("io.github.fso13.dependency-analyze")
}
```

(No `version` clause when resolving from `includeBuild`.)

---

## Publishing to the Gradle Plugin Portal

Maintainers: create API credentials on [plugins.gradle.org](https://plugins.gradle.org/), then:

```bash
export GRADLE_PUBLISH_KEY="<key>"
export GRADLE_PUBLISH_SECRET="<secret>"
```

Or add `gradle.publish.key` / `gradle.publish.secret` to `~/.gradle/gradle.properties`, and run:

```bash
./gradlew :build-logic:dependency-analyze-gradle-plugin:publishPlugins
```

---

## Links

- **Plugin Portal:** [https://plugins.gradle.org/plugin/io.github.fso13.dependency-analyze](https://plugins.gradle.org/plugin/io.github.fso13.dependency-analyze)
- **Repository:** [https://github.com/fso13/dependency-analyze](https://github.com/fso13/dependency-analyze)
