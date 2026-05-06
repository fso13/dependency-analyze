package com.github.fso13.depanalyze

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

internal class OssIndexClient(
  private val token: String?,
  private val logger: Logger,
) {
  private companion object {
    const val MAX_COMPONENTS_PER_REQUEST = 128
  }

  private val http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(20))
    .build()

  private val mapper: ObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  fun componentReports(purls: List<String>): Map<String, List<VulnerabilityInfo>> {
    if (purls.isEmpty()) return emptyMap()

    val uniquePurls = purls.distinct()
    val uri = URI("https://ossindex.sonatype.org/api/v3/component-report")
    val authHeader = bearerAuthHeaderOrNull(token)
    val result = linkedMapOf<String, List<VulnerabilityInfo>>()

    uniquePurls.chunked(MAX_COMPONENTS_PER_REQUEST).forEachIndexed { batchIdx, batch ->
      val bodyJson = mapper.writeValueAsString(mapOf("coordinates" to batch))
      val reqBuilder = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
      if (authHeader != null) reqBuilder.header("Authorization", authHeader)

      logger.lifecycle(
        "OSS Index request -> method=POST url={} batch={}/{} coordinates={} auth={}",
        uri.toString(),
        batchIdx + 1,
        (uniquePurls.size + MAX_COMPONENTS_PER_REQUEST - 1) / MAX_COMPONENTS_PER_REQUEST,
        batch.size,
        maskedAuthHeader(authHeader)
      )
      logger.lifecycle("OSS Index request body -> {}", bodyJson.take(4000))

      val req = reqBuilder.build()
      val resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      logger.lifecycle(
        "OSS Index response <- status={} batch={}/{} body={}",
        resp.statusCode(),
        batchIdx + 1,
        (uniquePurls.size + MAX_COMPONENTS_PER_REQUEST - 1) / MAX_COMPONENTS_PER_REQUEST,
        resp.body().take(4000)
      )

      if (resp.statusCode() !in 200..299) {
        val hint = if (resp.statusCode() == 401) {
          " OSS Index token is missing/invalid. Configure dependencyAnalyze.ossIndexToken."
        } else ""
        error("OSS Index request failed: HTTP ${resp.statusCode()} body=${resp.body().take(500)}.$hint")
      }

      val parsed: List<OssIndexComponentReport> =
        mapper.readValue(resp.body(), mapper.typeFactory.constructCollectionType(List::class.java, OssIndexComponentReport::class.java))
      parsed.forEach { report ->
        result[report.coordinates] = report.vulnerabilities.map { v -> v.toModel() }
      }
    }

    return result
  }

  private fun bearerAuthHeaderOrNull(token: String?): String? {
    if (token.isNullOrBlank()) return null
    return "Bearer $token"
  }

  private fun maskedAuthHeader(authHeader: String?): String {
    if (authHeader.isNullOrBlank()) return "<none>"
    val tokenPart = authHeader.removePrefix("Bearer ").trim()
    if (tokenPart.length <= 8) return "Bearer ****"
    return "Bearer ${tokenPart.take(4)}...${tokenPart.takeLast(4)}"
  }
}

internal data class OssIndexComponentReport(
  val coordinates: String,
  val vulnerabilities: List<OssIndexVulnerability> = emptyList(),
)

internal data class OssIndexVulnerability(
  val id: String,
  val title: String? = null,
  val description: String? = null,
  val cvssScore: Double? = null,
  val cvssVector: String? = null,
  val reference: String? = null,
  val recommendation: String? = null,
) {
  fun toModel() = VulnerabilityInfo(
    id = id,
    title = title,
    cvssScore = cvssScore,
    cvssVector = cvssVector,
    description = description,
    reference = reference,
    recommendation = recommendationWithTargetVersion(recommendation),
  )
}

private fun recommendationWithTargetVersion(recommendation: String?): String? {
  if (recommendation.isNullOrBlank()) return recommendation
  val version = extractTargetVersion(recommendation) ?: return recommendation
  val hasExplicitVersionMarker = recommendation.contains("Target version:", ignoreCase = true)
  if (hasExplicitVersionMarker) return recommendation
  return "$recommendation Target version: $version."
}

private fun extractTargetVersion(recommendation: String): String? {
  val patterns = listOf(
    // e.g. "Upgrade to version 1.2.3", "Upgrade to 2.0.0"
    """(?i)\bupgrade\s+to(?:\s+version)?\s+([0-9][0-9A-Za-z.\-+]*)""",
    // e.g. "Use version 3.1.4"
    """(?i)\buse\s+version\s+([0-9][0-9A-Za-z.\-+]*)""",
    // e.g. ">= 1.5.7"
    """(?i)\b>=\s*([0-9][0-9A-Za-z.\-+]*)"""
  )
  for (pattern in patterns) {
    val match = Regex(pattern).find(recommendation)
    if (match != null) return match.groupValues[1]
  }
  return null
}

