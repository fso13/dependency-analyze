plugins {
  kotlin("jvm") version "2.2.0" apply false
  id("io.github.fso13.dependency-analyze")
}

fun Project.readDotEnvToken(name: String): String? {
  val env = providers.environmentVariable(name).orNull
  if (!env.isNullOrBlank()) return env

  val dotEnv = rootProject.file(".env")
  if (!dotEnv.exists()) return null

  return dotEnv.readLines()
    .asSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
    .map {
      val idx = it.indexOf('=')
      it.substring(0, idx).trim() to it.substring(idx + 1).trim()
    }
    .firstOrNull { it.first == name }
    ?.second
    ?.takeIf { it.isNotBlank() }
}

dependencyAnalyze {
  // Example defaults; customize per your build.
  configurationNames.set(setOf("runtimeClasspath", "compileClasspath", "testRuntimeClasspath"))
  outputTitle.set("Dependency Analyze Report")
  vulnerabilityProvider.set("ossIndex")
  includeProjectDependencies.set(true)
  includeTransitives.set(true)

  // Priority: environment variable -> .env file.
  ossIndexToken.set(providers.provider { project.readDotEnvToken("OSSINDEX_TOKEN").orEmpty() })
}

subprojects {
  apply(plugin = "io.github.fso13.dependency-analyze")
  extensions.configure<com.github.fso13.depanalyze.DependencyAnalyzeExtension>("dependencyAnalyze") {
    configurationNames.set(setOf("runtimeClasspath", "compileClasspath", "testRuntimeClasspath"))
    outputTitle.set("Dependency Analyze Report")
    vulnerabilityProvider.set("ossIndex")
    includeProjectDependencies.set(true)
    includeTransitives.set(true)
    ossIndexToken.set(providers.provider { project.readDotEnvToken("OSSINDEX_TOKEN").orEmpty() })
  }
}

allprojects {
  group = "io.github.fso13"
  version = providers.gradleProperty("projectVersion").orElse("0.2.0").get()

  repositories {
    mavenCentral()
  }
}

