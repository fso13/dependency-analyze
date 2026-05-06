package com.github.fso13.depanalyze

import org.gradle.api.Plugin
import org.gradle.api.Project

class DependencyAnalyzePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val ext = project.extensions.create("dependencyAnalyze", DependencyAnalyzeExtension::class.java)
    val reportTaskName = "dependencyAnalyzeReport"
    val aggregateTaskName = "dependencyAnalyzeAggregate"

    // Sensible default output in root build directory.
    ext.outputFile.convention(project.layout.buildDirectory.file("reports/dependency-analyze/index.html"))

    project.tasks.register(reportTaskName, DependencyAnalyzeTask::class.java) { t ->
      t.group = "reporting"
      t.description = "Generates HTML dependency report (licenses + vulnerabilities)."

      t.configurationNames.set(ext.configurationNames)
      t.includeProjectDependencies.set(ext.includeProjectDependencies)
      t.includeTransitives.set(ext.includeTransitives)
      t.outputTitle.set(ext.outputTitle)

      t.vulnerabilityProvider.set(ext.vulnerabilityProvider)
      t.ossIndexToken.set(ext.ossIndexToken.orElse(""))
      t.ignoreVulnerabilityErrors.set(ext.ignoreVulnerabilityErrors)
      t.policyUri.set(ext.policyUri.orElse(""))

      t.outputFile.set(ext.outputFile)
    }

    ensureAggregateTaskRegistered(project, aggregateTaskName, reportTaskName)
  }

  private fun ensureAggregateTaskRegistered(project: Project, aggregateTaskName: String, reportTaskName: String) {
    val root = project.rootProject
    if (root.tasks.findByName(aggregateTaskName) == null) {
      root.tasks.register(aggregateTaskName, DependencyAnalyzeAggregateTask::class.java) { t ->
        t.group = "reporting"
        t.description = "Generates a single dependency report by aggregating all submodule reports."
        t.reportTaskName.set(reportTaskName)
        t.outputTitle.set("Dependency report")
        t.policyUri.set("")
        t.outputFile.set(root.layout.buildDirectory.file("reports/dependency-analyze/index.html"))
      }
    }

    root.gradle.projectsEvaluated {
      root.tasks.findByName(aggregateTaskName)?.dependsOn(
        root.allprojects.mapNotNull { it.tasks.findByName(reportTaskName) }
      )
    }
  }
}

