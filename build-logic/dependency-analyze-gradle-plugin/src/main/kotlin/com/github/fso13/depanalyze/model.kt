package com.github.fso13.depanalyze

data class DependencyReport(
  val generatedAtIso: String,
  val rootProject: String,
  val entries: List<DependencyEntry>,
)

data class DependencyEntry(
  val modulePath: String,
  val configuration: String,
  val type: DepType,
  val isTransitive: Boolean = false,
  val group: String?,
  val name: String,
  val version: String?,
  val purl: String?,
  val scope: String,
  val licenses: List<LicenseInfo>,
  val vulnerabilities: List<VulnerabilityInfo>,
  val latestVersion: String? = null,
)

enum class DepType { EXTERNAL, PROJECT }

data class LicenseInfo(
  val name: String?,
  val url: String?,
)

data class VulnerabilityInfo(
  val id: String,
  val title: String?,
  val cvssScore: Double?,
  val cvssVector: String?,
  val description: String?,
  val reference: String?,
  val recommendation: String?,
)

