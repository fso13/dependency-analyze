plugins {
  `java-gradle-plugin`
  kotlin("jvm")
  id("maven-publish")
  id("com.gradle.plugin-publish") version "1.3.1"
}

group = rootProject.group
version = rootProject.version

fun readDotEnvValue(name: String): String? {
  val dotEnv = rootProject.rootDir.parentFile.resolve(".env")
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

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())
  implementation("org.jsoup:jsoup:1.18.1")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

gradlePlugin {
  website.set("https://github.com/fso13/dependency-analize")
  vcsUrl.set("https://github.com/fso13/dependency-analize.git")

  plugins {
    create("dependencyAnalyze") {
      id = "io.github.fso13.dependency-analyze"
      implementationClass = "com.github.fso13.depanalyze.DependencyAnalyzePlugin"
      displayName = "Dependency Analyze Report"
      description = "Generates HTML report with dependency licenses and vulnerabilities."
      tags.set(listOf("dependencies", "security", "license", "report"))
    }
    create("dependencyAnalyzeCompat") {
      id = "io.github.fso13.dependency-analyze-gradle-plugin"
      implementationClass = "com.github.fso13.depanalyze.DependencyAnalyzePlugin"
      displayName = "Dependency Analyze Report (compat id)"
      description = "Compatibility alias for dependency analyze plugin id."
      tags.set(listOf("dependencies", "security", "license", "report"))
    }
  }
}

kotlin {
  jvmToolchain(17)
}

//publishing {
//  repositories {
//    mavenLocal()
//    maven {
//      name = "nexus"
//      credentials {
//        username = findProperty("nexus.username")?.toString()
//          ?: System.getenv("NEXUS_USERNAME")
//          ?: readDotEnvValue("NEXUS_USERNAME")
//          ?: ""
//        password = findProperty("nexus.password")?.toString()
//          ?: System.getenv("NEXUS_PASSWORD")
//          ?: readDotEnvValue("NEXUS_PASSWORD")
//          ?: ""
//      }
//      url = uri("https://nexus.lukit.ru/repository/libs-jaga/")
//    }
//  }
//}


