plugins {
  kotlin("jvm") version "2.2.0" apply false
}

fun parentRootVersion(): String {
  val propsFile = rootDir.parentFile.resolve("gradle.properties")
  if (!propsFile.exists()) return "0.2.0"
  val props = java.util.Properties()
  propsFile.inputStream().use { props.load(it) }
  return props.getProperty("projectVersion")?.trim().takeUnless { it.isNullOrEmpty() } ?: "0.2.0"
}

allprojects {
  group = "io.github.fso13"
  version = parentRootVersion()

  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

