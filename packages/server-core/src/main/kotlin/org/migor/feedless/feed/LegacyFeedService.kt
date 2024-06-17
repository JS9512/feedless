package org.migor.feedless.feed

import org.migor.feedless.AppProfiles
import org.migor.feedless.api.dto.RichArticle
import org.migor.feedless.api.dto.RichFeed
import org.migor.feedless.common.PropertyService
import org.migor.feedless.data.jpa.enums.EntityVisibility
import org.migor.feedless.data.jpa.enums.ReleaseStatus
import org.migor.feedless.document.DocumentDAO
import org.migor.feedless.generated.types.ScrapeDebugOptions
import org.migor.feedless.generated.types.ScrapePage
import org.migor.feedless.generated.types.ScrapePrerender
import org.migor.feedless.generated.types.ScrapeRequest
import org.migor.feedless.repository.RepositoryDAO
import org.migor.feedless.repository.toRichArticle
import org.migor.feedless.service.ScrapeService
import org.migor.feedless.user.UserDAO
import org.migor.feedless.util.FeedUtil
import org.migor.feedless.util.HtmlUtil
import org.migor.feedless.web.ExtendContext
import org.migor.feedless.web.GenericFeedSelectors
import org.migor.feedless.web.WebToFeedTransformer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class LegacyFeedService {

  @Autowired
  private lateinit var propertyService: PropertyService

  @Autowired
  private lateinit var webToFeedTransformer: WebToFeedTransformer

  @Autowired
  private lateinit var feedParserService: FeedParserService

  @Autowired
  private lateinit var scrapeService: ScrapeService

  @Autowired
  private lateinit var repositoryDAO: RepositoryDAO

  @Autowired
  private lateinit var userDAO: UserDAO

  @Autowired
  private lateinit var documentDAO: DocumentDAO

  @Autowired
  private lateinit var environment: Environment

  fun getRepoTitleForLegacyFeedNotifications(): String = "legacyFeedNotifications"

  private fun eolFeed(url: String?): RichFeed {
    val feed = createEolFeed()
    feed.items = listOf(createEolArticle(url))
    return feed
  }

  private fun createEolFeed(): RichFeed {
    val feed = RichFeed()
    feed.id = "rss-proxy:2"
    feed.title = "End Of Life"
    feed.feedUrl = ""
    feed.expired = true
    feed.publishedAt = Date()
    return feed
  }

  private fun createEolArticle(url: String?): RichArticle {
    val article = RichArticle()
    val preregistrationLink = if (url == null) {
      propertyService.appHost
    } else {
      "${propertyService.appHost}?url=${URLEncoder.encode(url, StandardCharsets.UTF_8)}"
    }

    article.id = FeedUtil.toURI("end-of-life", preregistrationLink)
    article.title = "Important Notice: Termination of Stateless RSS-Proxy! Stateful RSS-Proxy is here!!"
    article.contentHtml = """Dear User,

I hope this message finds you well. I am writing to inform you of some changes regarding our services that may affect you.

As of now, I regret to terminate of our RSS-Proxy and I sincerely apologize for any inconvenience this may cause.

However, I am excited to share that we transitioned to a new and improved version. We believe this upgrade will enhance your experience and better meet your needs.

You have the opportunity to register here $preregistrationLink and import your existing RSS-proxy urls.

Should you have any questions, concerns, reach out to me at feedlessapp@proton.me.

Best regards,

Markus

    """.trimIndent()
//    article.contentText = "Thanks for using rssproxy or feedless. I have terminated the service has has ended. You may migrate to the latest version using this link $migrationUrl"
    article.url = preregistrationLink
    article.publishedAt = Date()
    return article
  }

  fun getFeed(feedId: String): RichFeed {
    TODO("Not yet implemented")
  }

  fun webToFeed(
    corrId: String,
    url: String,
    linkXPath: String,
    extendContext: String,
    contextXPath: String,
    dateXPath: String?,
    prerender: Boolean,
    puppeteerScript: String?,
    filter: String?
  ): RichFeed {
    return if (legacySupport()) {
      val scrapeRequest = ScrapeRequest.newBuilder()
        .corrId(corrId)
        .debug(ScrapeDebugOptions.newBuilder()
          .html(true)
          .build())
        .page(
          ScrapePage.newBuilder()
            .url(url)
            .prerender(
              if (prerender) {
                ScrapePrerender.newBuilder().build()
              } else {
                null
              }
            )
            .build()
        )
        .emit(emptyList())
        .build()
      val scrapeResponse = scrapeService.scrape(corrId, scrapeRequest).block()!!

      val selectors = GenericFeedSelectors(
        linkXPath = linkXPath,
        extendContext = when(extendContext) {
          "p" -> ExtendContext.PREVIOUS
          "n" -> ExtendContext.NEXT
          "pn" -> ExtendContext.PREVIOUS_AND_NEXT
          else -> ExtendContext.NONE
        },
        contextXPath = contextXPath,
        dateXPath = dateXPath,
      )

      appendNotifications(corrId, webToFeedTransformer.getFeedBySelectors(
        corrId,
        selectors,
        HtmlUtil.parseHtml(scrapeResponse.debug.html, url),
        URL(url)
      ))
    } else {
      eolFeed(url)
    }
  }

  fun transformFeed(corrId: String, feedUrl: String, filter: String?): RichFeed {
    return if (legacySupport()) {
      appendNotifications(corrId, feedParserService.parseFeedFromUrl(corrId, feedUrl))
    } else {
      eolFeed(feedUrl)
    }
  }

  private fun appendNotifications(corrId: String, feed: RichFeed): RichFeed {
    val root = userDAO.findFirstByRootIsTrue()
    val repo = repositoryDAO.findByTitleAndOwnerId(getRepoTitleForLegacyFeedNotifications(), root!!.id)
    val pageRequest = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "publishedAt"))
    val documents = documentDAO.findAllByRepositoryIdAndStatusAndPublishedAtBefore(repo.id, ReleaseStatus.released, Date(), pageRequest)
    feed.items = documents.map { it.toRichArticle(propertyService, EntityVisibility.isPublic) }.plus(feed.items)
    return feed
  }

  private fun legacySupport(): Boolean {
    return environment.acceptsProfiles(Profiles.of(AppProfiles.legacyFeeds))
  }

}
