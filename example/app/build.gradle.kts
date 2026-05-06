plugins {
  application
  kotlin("jvm")
}

dependencies {
  implementation(project(":example:lib"))
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // Source: https://mvnrepository.com/artifact/com.itextpdf/itextpdf
  implementation("com.itextpdf:itextpdf:5.0.6")
}

application {
  mainClass.set("com.github.fso13.app.MainKt")
}

kotlin {
  jvmToolchain(17)
}

