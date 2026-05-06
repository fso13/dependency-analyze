rootProject.name = "dependency-analyze"

pluginManagement {
  includeBuild("build-logic")
}

include(
  "example:app",
  "example:lib",
)

