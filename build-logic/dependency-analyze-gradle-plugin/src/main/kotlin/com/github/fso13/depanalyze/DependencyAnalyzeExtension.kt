package com.github.fso13.depanalyze

import org.gradle.api.model.ObjectFactory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

abstract class DependencyAnalyzeExtension @Inject constructor(objects: ObjectFactory) {
  /**
   * If empty, plugin uses all resolvable configurations.
   * Typical choices: runtimeClasspath, compileClasspath, testRuntimeClasspath.
   */
  @get:Input
  abstract val configurationNames: SetProperty<String>

  @get:Input
  abstract val includeProjectDependencies: Property<Boolean>

  @get:Input
  abstract val includeTransitives: Property<Boolean>

  @get:Input
  abstract val outputTitle: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  /**
   * Vulnerability provider. Supported values:
   * - "ossIndex" (default) – Sonatype OSS Index (optional creds)
   * - "none"
   */
  @get:Input
  abstract val vulnerabilityProvider: Property<String>

  @get:Input
  @get:Optional
  abstract val ossIndexToken: Property<String>

  /**
   * When true, the task will not fail if vulnerability provider errors.
   */
  @get:Input
  abstract val ignoreVulnerabilityErrors: Property<Boolean>

  /**
   * Optional link to your internal policy page shown in report header.
   */
  @get:Input
  @get:Optional
  abstract val policyUri: Property<String>

  init {
    configurationNames.convention(emptySet())
    includeProjectDependencies.convention(true)
    includeTransitives.convention(true)
    outputTitle.convention("Dependency report")
    vulnerabilityProvider.convention("ossIndex")
    ignoreVulnerabilityErrors.convention(true)
  }
}

