plugins {
  kotlin("jvm")
}

dependencies {
  implementation("com.squareup.okio:okio:3.10.2")
}

kotlin {
  jvmToolchain(17)
}

