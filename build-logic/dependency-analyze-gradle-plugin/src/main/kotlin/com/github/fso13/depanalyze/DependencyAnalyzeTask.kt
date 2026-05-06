package com.github.fso13.depanalyze

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Base64
import java.io.File
import org.w3c.dom.Element

abstract class DependencyAnalyzeTask : DefaultTask() {
  @get:Input
  abstract val configurationNames: SetProperty<String>

  @get:Input
  abstract val includeProjectDependencies: Property<Boolean>

  @get:Input
  abstract val includeTransitives: Property<Boolean>

  @get:Input
  abstract val outputTitle: Property<String>

  @get:Input
  abstract val vulnerabilityProvider: Property<String>

  @get:Input
  abstract val ossIndexToken: Property<String>

  @get:Input
  abstract val ignoreVulnerabilityErrors: Property<Boolean>

  @get:Input
  abstract val policyUri: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val entries = mutableListOf<DependencyEntry>()
    val projectsToScan = listOf(project)
    for (p in projectsToScan) {
      val configs = targetConfigurations(p, configurationNames.get())
      for (cfg in configs) {
        val declaredExternalGa = cfg.allDependencies
          .filterIsInstance<ExternalModuleDependency>()
          .mapNotNull { dep ->
            val g = dep.group?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            g to dep.name
          }
          .toSet()

        val includeTransitively = includeTransitives.get()
        val (externalByGa, resolvedSuccessfully) = tryResolveExternalDependencies(cfg, includeTransitively)

        val declaredExternalEntries = cfg.allDependencies.filterIsInstance<ExternalModuleDependency>().map { dep ->
          val g = dep.group
          val n = dep.name
          val v = dep.version ?: if (!g.isNullOrBlank()) resolvedVersionsByGa(cfg)["$g:$n"] else null
          DependencyEntry(
            modulePath = p.path,
            configuration = cfg.name,
            type = DepType.EXTERNAL,
            isTransitive = false,
            group = g,
            name = n,
            version = v,
            purl = if (!g.isNullOrBlank() && !v.isNullOrBlank()) purl(g, n, v) else null,
            scope = cfg.name,
            licenses = emptyList(),
            vulnerabilities = emptyList(),
          )
        }

        val externalEntries = if (resolvedSuccessfully) {
          externalByGa.map {
            DependencyEntry(
              modulePath = p.path,
              configuration = cfg.name,
              type = DepType.EXTERNAL,
              isTransitive = !declaredExternalGa.contains(it.group to it.name),
              group = it.group,
              name = it.name,
              version = it.version,
              purl = purl(it.group, it.name, it.version),
              scope = cfg.name,
              licenses = emptyList(),
              vulnerabilities = emptyList(),
            )
          }
        } else {
          logger.warn("Unable to resolve ${p.path}:${cfg.name}. Falling back to declared dependencies only.")
          declaredExternalEntries
        }

        entries += externalEntries

        cfg.allDependencies.forEach { dep ->
          when (dep) {
            is ExternalModuleDependency -> Unit
            is ProjectDependency -> {
              if (includeProjectDependencies.get()) {
                entries += DependencyEntry(
                  modulePath = p.path,
                  configuration = cfg.name,
                  type = DepType.PROJECT,
                  isTransitive = false,
                  group = null,
                  name = projectDependencyPath(dep),
                  version = null,
                  purl = null,
                  scope = cfg.name,
                  licenses = emptyList(),
                  vulnerabilities = emptyList(),
                )
              }
            }
          }
        }
      }
    }

    val distinctEntries = entries.distinctBy {
      listOf(it.modulePath, it.configuration, it.type.name, it.isTransitive.toString(), it.group ?: "", it.name, it.version ?: "").joinToString("|")
    }

    val external = distinctEntries
      .filter { it.type == DepType.EXTERNAL && it.group != null && it.version != null }
      .distinctBy { Triple(it.group, it.name, it.version) }

    val latestVersionsByGa = resolveLatestVersions(distinctEntries)
    val licensesByGav = resolveLicenses(external)
    val vulnsByPurl = resolveVulnerabilities(external.mapNotNull { it.purl })

    val enriched = distinctEntries.map { e ->
      val licenses = if (e.type == DepType.EXTERNAL && e.group != null && e.version != null) {
        licensesByGav[Triple(e.group, e.name, e.version)].orEmpty()
      } else emptyList()

      val vulns = e.purl?.let { vulnsByPurl[it] }.orEmpty()

      val latest = if (e.type == DepType.EXTERNAL && e.group != null) {
        latestVersionsByGa[e.group to e.name]
      } else {
        null
      }

      e.copy(licenses = licenses, vulnerabilities = vulns, latestVersion = latest)
    }

    val report = DependencyReport(
      generatedAtIso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      rootProject = project.rootProject.name,
      entries = enriched.sortedWith(compareBy({ it.modulePath }, { it.configuration }, { it.type.name }, { it.group ?: "" }, { it.name }, { it.version ?: "" })),
    )

    val html = HtmlReportRenderer().render(
      title = outputTitle.get(),
      policyUri = policyUri.orNull,
      report = report,
    )

    val out = outputFile.get().asFile
    out.parentFile.mkdirs()
    out.writeText(html, Charsets.UTF_8)

    logger.lifecycle("Dependency report written to: ${out.absolutePath}")
  }

  private fun resolveVulnerabilities(purls: List<String>): Map<String, List<VulnerabilityInfo>> {
    return when (vulnerabilityProvider.get().lowercase()) {
      "none" -> emptyMap()
      "ossindex" -> {
        val token = ossIndexToken.orNull
        if (token.isNullOrBlank()) {
          logger.warn(
            "OSS Index token is not configured. " +
              "API can return 401 and vulnerabilities may be missing. " +
              "Set dependencyAnalyze.ossIndexToken."
          )
        }
        try {
          OssIndexClient(
            token = token,
            logger = logger,
          ).componentReports(purls)
        } catch (e: Exception) {
          if (ignoreVulnerabilityErrors.get()) {
            logger.warn("Vulnerability lookup failed: ${e.message}")
            emptyMap()
          } else {
            throw e
          }
        }
      }
      else -> emptyMap()
    }
  }

  private fun resolveLicenses(externals: List<DependencyEntry>): Map<Triple<String, String, String>, List<LicenseInfo>> {
    val repos = resolveMavenRepos()

    val http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build()

    val out = linkedMapOf<Triple<String, String, String>, List<LicenseInfo>>()
    val cache = hashMapOf<String, List<LicenseInfo>>()
    val pomTextCache = hashMapOf<String, String?>()

    for (e in externals) {
      val g = e.group ?: continue
      val v = e.version ?: continue
      val a = e.name
      val key = "$g:$a:$v"

      val cached = cache[key]
      if (cached != null) {
        out[Triple(g, a, v)] = cached
        continue
      }
      val licenses = resolveLicensesFromPomHierarchy(
        group = g,
        artifact = a,
        version = v,
        repos = repos,
        http = http,
        pomTextCache = pomTextCache,
      )
      cache[key] = licenses
      out[Triple(g, a, v)] = licenses
    }

    return out
  }

  private fun resolveLatestVersions(entries: List<DependencyEntry>): Map<Pair<String, String>, String> {
    val externalGa = entries
      .asSequence()
      .filter { it.type == DepType.EXTERNAL && !it.group.isNullOrBlank() }
      .map { (it.group ?: "") to it.name }
      .distinct()
      .toList()
    if (externalGa.isEmpty()) return emptyMap()

    val repos = resolveMavenRepos()
    if (repos.isEmpty()) return emptyMap()

    val http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build()

    val result = linkedMapOf<Pair<String, String>, String>()
    val metadataCache = hashMapOf<String, String?>()

    for ((group, artifact) in externalGa) {
      val latest = resolveLatestVersionFromMetadata(
        group = group,
        artifact = artifact,
        repos = repos,
        http = http,
        metadataCache = metadataCache,
      ) ?: continue
      result[group to artifact] = latest
    }
    return result
  }

  private fun resolveLatestVersionFromMetadata(
    group: String,
    artifact: String,
    repos: List<MavenRepoAccess>,
    http: HttpClient,
    metadataCache: MutableMap<String, String?>,
  ): String? {
    val key = "$group:$artifact"
    if (metadataCache.containsKey(key)) return metadataCache[key]

    val metadataPath = "${group.replace('.', '/')}/$artifact/maven-metadata.xml"
    val metadata = repos.asSequence()
      .mapNotNull { repo ->
        fetchText(http, URI("${repo.baseUrl}/$metadataPath"), repo)
      }
      .firstOrNull()

    val latest = metadata?.let { parseLatestVersionFromMetadata(it) }
    metadataCache[key] = latest
    return latest
  }

  private fun parseLatestVersionFromMetadata(xml: String): String? {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.isNamespaceAware = false
    val doc = runCatching { dbf.newDocumentBuilder().parse(InputSource(StringReader(xml))) }.getOrNull() ?: return null
    val versioning = doc.getElementsByTagName("versioning").item(0) as? Element ?: return null

    fun childText(parent: Element, tag: String): String? {
      val nodes = parent.getElementsByTagName(tag)
      if (nodes.length == 0) return null
      return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    val release = childText(versioning, "release")
    if (!release.isNullOrBlank()) return release

    val latest = childText(versioning, "latest")
    if (!latest.isNullOrBlank()) return latest

    val versionsNode = versioning.getElementsByTagName("versions").item(0) as? Element ?: return null
    val versions = versionsNode
      .getElementsByTagName("version")
      .let { nodes -> (0 until nodes.length).mapNotNull { idx -> nodes.item(idx)?.textContent?.trim()?.takeIf { it.isNotBlank() } } }
    return versions.lastOrNull()
  }

  private fun resolveMavenRepos(): List<MavenRepoAccess> {
    return project.rootProject.allprojects
      .flatMap { p -> p.repositories.withType(MavenArtifactRepository::class.java).toList() }
      .mapNotNull { r ->
        val base = r.url?.toString()?.trimEnd('/') ?: return@mapNotNull null
        val creds = r.credentials as? PasswordCredentials
        MavenRepoAccess(
          baseUrl = base,
          username = creds?.username?.takeIf { !it.isNullOrBlank() },
          password = creds?.password?.takeIf { !it.isNullOrBlank() },
        )
      }
      .distinctBy { it.baseUrl + "|" + (it.username ?: "") }
  }

  private fun fetchText(http: HttpClient, uri: URI, repo: MavenRepoAccess): String? {
    val reqBuilder = HttpRequest.newBuilder()
      .uri(uri)
      .timeout(Duration.ofSeconds(30))
      .GET()
    val auth = basicAuthHeaderOrNull(repo.username, repo.password)
    if (auth != null) reqBuilder.header("Authorization", auth)
    val req = reqBuilder.build()

    val resp = try {
      http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    } catch (_: Exception) {
      return null
    }

    if (resp.statusCode() !in 200..299) return null
    return resp.body()
  }

  private fun parsePomLicenses(pomXml: String): List<LicenseInfo> {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.isNamespaceAware = false

    val doc = dbf.newDocumentBuilder().parse(InputSource(StringReader(pomXml)))

    val nodes = doc.getElementsByTagName("license")
    if (nodes.length == 0) return emptyList()

    val licenses = mutableListOf<LicenseInfo>()
    for (i in 0 until nodes.length) {
      val n = nodes.item(i)
      val children = n.childNodes
      var name: String? = null
      var url: String? = null
      for (j in 0 until children.length) {
        val c = children.item(j)
        when (c.nodeName) {
          "name" -> name = c.textContent?.trim()
          "url" -> url = c.textContent?.trim()
        }
      }
      if (!name.isNullOrBlank() || !url.isNullOrBlank()) licenses += LicenseInfo(name = name, url = url)
    }
    return licenses.distinctBy { (it.name ?: "") + "|" + (it.url ?: "") }
  }

  private fun resolveLicensesFromPomHierarchy(
    group: String,
    artifact: String,
    version: String,
    repos: List<MavenRepoAccess>,
    http: HttpClient,
    pomTextCache: MutableMap<String, String?>,
  ): List<LicenseInfo> {
    val visited = hashSetOf<String>()
    var current: GAV? = GAV(group, artifact, version)
    var depth = 0
    while (current != null && depth < 8) {
      val key = "${current.group}:${current.artifact}:${current.version}"
      if (!visited.add(key)) break
      val pomText = loadPomText(current.group, current.artifact, current.version, repos, http, pomTextCache) ?: break
      val info = parsePomInfo(pomText)
      if (info.licenses.isNotEmpty()) return info.licenses
      current = info.parent
      depth++
    }
    return emptyList()
  }

  private fun loadPomText(
    group: String,
    artifact: String,
    version: String,
    repos: List<MavenRepoAccess>,
    http: HttpClient,
    pomTextCache: MutableMap<String, String?>,
  ): String? {
    val key = "$group:$artifact:$version"
    if (pomTextCache.containsKey(key)) return pomTextCache[key]

    val pomPath = "${group.replace('.', '/')}/$artifact/$version/$artifact-$version.pom"
    val fromRepos = repos.asSequence()
      .mapNotNull { repo ->
        val uri = URI("${repo.baseUrl}/$pomPath")
        fetchText(http, uri, repo)
      }
      .firstOrNull()

    val result = fromRepos
      ?: pomTextFromLocalCaches(group, artifact, version)

    pomTextCache[key] = result
    return result
  }

  private fun purl(group: String, artifact: String, version: String): String {
    return "pkg:maven/$group/$artifact@$version"
  }

  private fun pomTextFromLocalCaches(group: String, artifact: String, version: String): String? {
    val gradleCachePom = findPomInGradleCache(group, artifact, version)
    if (gradleCachePom != null) {
      return runCatching { gradleCachePom.readText(Charsets.UTF_8) }.getOrNull()
    }

    val m2Pom = findPomInM2(group, artifact, version)
    if (m2Pom != null) {
      return runCatching { m2Pom.readText(Charsets.UTF_8) }.getOrNull()
    }

    return null
  }

  private fun findPomInGradleCache(group: String, artifact: String, version: String): File? {
    val base = project.gradle.gradleUserHomeDir
      .resolve("caches/modules-2/files-2.1")
      .resolve(group)
      .resolve(artifact)
      .resolve(version)
    if (!base.exists() || !base.isDirectory) return null

    val targetName = "$artifact-$version.pom"
    val dirs = base.listFiles()?.filter { it.isDirectory }.orEmpty()
    for (dir in dirs) {
      val candidate = dir.resolve(targetName)
      if (candidate.exists() && candidate.isFile) return candidate
    }
    return null
  }

  private fun findPomInM2(group: String, artifact: String, version: String): File? {
    val home = System.getProperty("user.home") ?: return null
    val base = File(home)
      .resolve(".m2/repository")
      .resolve(group.replace('.', '/'))
      .resolve(artifact)
      .resolve(version)
    if (!base.exists() || !base.isDirectory) return null
    val candidate = base.resolve("$artifact-$version.pom")
    return if (candidate.exists() && candidate.isFile) candidate else null
  }

  private fun basicAuthHeaderOrNull(username: String?, password: String?): String? {
    if (username.isNullOrBlank() || password.isNullOrBlank()) return null
    val raw = "$username:$password"
    return "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
  }

  private fun parsePomInfo(pomXml: String): PomInfo {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.isNamespaceAware = false
    val doc = dbf.newDocumentBuilder().parse(InputSource(StringReader(pomXml)))

    val licenses = parsePomLicenses(pomXml)

    val parentNodes = doc.getElementsByTagName("parent")
    if (parentNodes.length == 0) return PomInfo(licenses = licenses, parent = null)
    val parent = parentNodes.item(0)
    val children = parent.childNodes
    var g: String? = null
    var a: String? = null
    var v: String? = null
    for (i in 0 until children.length) {
      val n = children.item(i)
      when (n.nodeName) {
        "groupId" -> g = n.textContent?.trim()
        "artifactId" -> a = n.textContent?.trim()
        "version" -> v = n.textContent?.trim()
      }
    }
    val parentGav = if (!g.isNullOrBlank() && !a.isNullOrBlank() && !v.isNullOrBlank()) GAV(g, a, v) else null
    return PomInfo(licenses = licenses, parent = parentGav)
  }

  private fun projectDependencyPath(dep: ProjectDependency): String {
    try {
      val m = dep.javaClass.methods.firstOrNull { it.name == "getPath" && it.parameterCount == 0 }
      val value = m?.invoke(dep) as? String
      if (!value.isNullOrBlank()) return value
    } catch (_: Throwable) {
    }
    try {
      val m = dep.javaClass.methods.firstOrNull { it.name == "getDependencyProject" && it.parameterCount == 0 }
      val projectObj = m?.invoke(dep)
      val pathM = projectObj?.javaClass?.methods?.firstOrNull { it.name == "getPath" && it.parameterCount == 0 }
      val value = pathM?.invoke(projectObj) as? String
      if (!value.isNullOrBlank()) return value
    } catch (_: Throwable) {
    }
    return dep.toString()
  }

  private fun resolvedVersionsByGa(cfg: org.gradle.api.artifacts.Configuration): Map<String, String> {
    return runCatching {
      cfg.incoming.resolutionResult.allComponents
        .mapNotNull { c ->
          val id = c.id as? ModuleComponentIdentifier ?: return@mapNotNull null
          "${id.group}:${id.module}" to id.version
        }
        .toMap()
    }.getOrDefault(emptyMap())
  }

  private fun tryResolveExternalDependencies(
    cfg: org.gradle.api.artifacts.Configuration,
    includeTransitively: Boolean,
  ): Pair<List<ResolvedExternalDependency>, Boolean> {
    return runCatching {
      val root = cfg.incoming.resolutionResult.root
      val firstLevel = root.dependencies
        .filterIsInstance<ResolvedDependencyResult>()
        .mapNotNull { dep -> dep.selected.id as? ModuleComponentIdentifier }

      val all = cfg.incoming.resolutionResult.allComponents
        .mapNotNull { c -> c.id as? ModuleComponentIdentifier }

      val ids = if (includeTransitively) all else firstLevel
      ids.map { id ->
        ResolvedExternalDependency(
          group = id.group,
          name = id.module,
          version = id.version,
        )
      }
        .distinctBy { "${it.group}:${it.name}:${it.version}" }
    }.fold(
      onSuccess = { it to true },
      onFailure = { emptyList<ResolvedExternalDependency>() to false }
    )
  }
}

private data class MavenRepoAccess(
  val baseUrl: String,
  val username: String?,
  val password: String?,
)

private data class GAV(
  val group: String,
  val artifact: String,
  val version: String,
)

private data class PomInfo(
  val licenses: List<LicenseInfo>,
  val parent: GAV?,
)

private data class ResolvedExternalDependency(
  val group: String,
  val name: String,
  val version: String,
)

private fun targetConfigurations(project: Project, configured: Set<String>): List<org.gradle.api.artifacts.Configuration> {
  val all = project.configurations.filter { it.isCanBeResolved }
  if (configured.isEmpty()) return all
  val byName = all.associateBy { it.name }
  return configured.mapNotNull { byName[it] }
}
