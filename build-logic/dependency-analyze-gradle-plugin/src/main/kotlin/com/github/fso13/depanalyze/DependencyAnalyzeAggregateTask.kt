package com.github.fso13.depanalyze

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

abstract class DependencyAnalyzeAggregateTask : DefaultTask() {
  @get:Input
  abstract val reportTaskName: Property<String>

  @get:Input
  abstract val outputTitle: Property<String>

  @get:Input
  abstract val policyUri: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val mapper = jacksonObjectMapper()
    val reports = project.rootProject.allprojects
      .mapNotNull { p ->
        val reportTask = p.tasks.findByName(reportTaskName.get()) as? DependencyAnalyzeTask ?: return@mapNotNull null
        val reportFile = reportTask.outputFile.orNull?.asFile ?: return@mapNotNull null
        if (!reportFile.exists()) {
          logger.warn("Dependency report was not found for ${p.path}: ${reportFile.absolutePath}")
          return@mapNotNull null
        }
        parseReport(reportFile.readText(Charsets.UTF_8), mapper)
      }

    if (reports.isEmpty()) {
      throw GradleException("No module reports found. Run ${reportTaskName.get()} in modules first.")
    }

    val entries = reports
      .flatMap { it.entries }
      .distinctBy { e ->
        listOf(e.modulePath, e.configuration, e.type.name, e.isTransitive.toString(), e.group ?: "", e.name, e.version ?: "").joinToString("|")
      }
      .sortedWith(compareBy({ it.modulePath }, { it.configuration }, { it.type.name }, { it.group ?: "" }, { it.name }, { it.version ?: "" }))

    val report = DependencyReport(
      generatedAtIso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      rootProject = project.rootProject.name,
      entries = entries,
    )

    val html = HtmlReportRenderer().render(
      title = outputTitle.get(),
      policyUri = policyUri.orNull,
      report = report,
    )

    val out = outputFile.get().asFile
    out.parentFile.mkdirs()
    out.writeText(html, Charsets.UTF_8)
    logger.lifecycle("Aggregated dependency report written to: ${out.absolutePath}")
  }

  private fun parseReport(html: String, mapper: com.fasterxml.jackson.databind.ObjectMapper): DependencyReport {
    val doc = Jsoup.parse(html)
    val raw = doc.getElementById("report-data")?.data()?.takeIf { it.isNotBlank() }
      ?: throw GradleException("Unable to parse report-data from dependency report HTML")
    return mapper.readValue(raw)
  }
}
