package org.migor.feedless.repository

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import org.migor.feedless.AppMetrics
import org.migor.feedless.AppProfiles
import org.migor.feedless.feed.exporter.FeedExporter
import org.migor.feedless.util.HttpUtil.createCorrId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@Controller
@Profile(AppProfiles.database)
class RepositoryController {

  private lateinit var feedXsl: String
  private val log = LoggerFactory.getLogger(RepositoryController::class.simpleName)

  @Autowired
  lateinit var repositoryService: RepositoryService

  @Autowired
  lateinit var meterRegistry: MeterRegistry

  @Autowired
  lateinit var feedExporter: FeedExporter

  @GetMapping(
    "/feed/{repositoryId}/atom",
    "/f/{repositoryId}/atom", produces = ["application/atom+xml;charset=UTF-8"]
  )
  fun atomFeed(
    request: HttpServletRequest,
    @PathVariable("repositoryId") repositoryId: String,
    @RequestParam("page", required = false, defaultValue = "0") page: Int
  ): ResponseEntity<String> {
    val corrId = createCorrId(request)
    meterRegistry.counter(
      AppMetrics.fetchRepository, listOf(
        Tag.of("type", "repository"),
        Tag.of("id", repositoryId),
      )
    ).increment()
    log.info("[$corrId] GET feed/atom id=$repositoryId page=$page")
    return feedExporter.to(
      corrId,
      HttpStatus.OK,
      "atom",
      repositoryService.getFeedByRepositoryId(repositoryId, page)
    )
  }

  @GetMapping(
    "/feed/{repositoryId}/json",
    "/feed/{repositoryId}",
    "/f/{repositoryId}/json",
    "/f/{repositoryId}",
    produces = ["application/json;charset=UTF-8"]
  )
  fun jsonFeed(
    request: HttpServletRequest,
    @PathVariable("repositoryId") repositoryId: String,
    @RequestParam("page", required = false, defaultValue = "0") page: Int
  ): ResponseEntity<String> {
    val corrId = createCorrId(request)
    meterRegistry.counter(
      AppMetrics.fetchRepository, listOf(
        Tag.of("type", "repository"),
        Tag.of("id", repositoryId),
      )
    ).increment()
    log.info("[$corrId] GET feed/json id=$repositoryId page=$page")
    return feedExporter.to(
      corrId,
      HttpStatus.OK,
      "json",
      repositoryService.getFeedByRepositoryId(repositoryId, page)
    )
  }

  @GetMapping(
    "/feed/static/feed.xsl", produces = ["text/xsl"]
  )
  fun xsl(request: HttpServletRequest): ResponseEntity<String> {
    return ResponseEntity.ok(feedXsl)
  }

  @PostConstruct
  fun postConstruct() {
    val scanner = Scanner(ClassPathResource("/feed.xsl", this.javaClass.classLoader).inputStream)
    val data = StringBuilder()
    while(scanner.hasNextLine()) {
      data.appendLine(scanner.nextLine())
    }
    feedXsl = data.toString()
  }
}
