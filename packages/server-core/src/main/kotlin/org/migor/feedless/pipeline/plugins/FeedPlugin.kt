package org.migor.feedless.pipeline.plugins

import org.migor.feedless.AppProfiles
import org.migor.feedless.actions.ExecuteActionEntity
import org.migor.feedless.common.HttpResponse
import org.migor.feedless.feed.FeedParserService
import org.migor.feedless.generated.types.FeedlessPlugins
import org.migor.feedless.generated.types.MimeData
import org.migor.feedless.generated.types.ScrapeExtractFragment
import org.migor.feedless.generated.types.SelectorsInput
import org.migor.feedless.pipeline.FragmentOutput
import org.migor.feedless.pipeline.FragmentTransformerPlugin
import org.migor.feedless.util.HtmlUtil
import org.migor.feedless.util.JsonUtil
import org.migor.feedless.web.ExtendContext
import org.migor.feedless.web.GenericFeedRule
import org.migor.feedless.web.Selectors
import org.migor.feedless.web.WebExtractService.Companion.MIME_URL
import org.migor.feedless.web.WebToFeedTransformer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.net.URL
import java.nio.charset.StandardCharsets

@Service
@Profile(AppProfiles.scrape)
class FeedPlugin : FragmentTransformerPlugin {

  private val log = LoggerFactory.getLogger(FeedPlugin::class.simpleName)

  @Autowired
  private lateinit var webToFeedTransformer: WebToFeedTransformer

  @Lazy
  @Autowired
  private lateinit var feedParserService: FeedParserService

  override fun id(): String = FeedlessPlugins.org_feedless_feed.name
  override fun listed() = true

  override fun transformFragment(
      corrId: String,
      action: ExecuteActionEntity,
      data: HttpResponse,
      logger: (String) -> Unit,
  ): FragmentOutput {
    val executorParams = action.executorParams!!
    log.debug("[$corrId] transformFragment using selectors ${JsonUtil.gson.toJson(executorParams.org_feedless_feed)}")

    val document = HtmlUtil.parseHtml(data.responseBody.toString(StandardCharsets.UTF_8), data.url)

    val feed = (executorParams.org_feedless_feed?.generic?.let {
      webToFeedTransformer.getFeedBySelectors(
        corrId, it.toSelectors(),
        document, URL(data.url)
      )
    } ?: feedParserService.parseFeed(corrId, data))
    log.debug("[$corrId] transformed to feed with ${feed.items.size} items")

    return FragmentOutput(
      fragmentName = id(),
      items = feed.items,
      fragments = feed.links?.map { ScrapeExtractFragment(data = MimeData(data = it, mimeType = MIME_URL)) }
    )
  }

  override fun name(): String = "Feed"
}

private fun SelectorsInput.toSelectors(): Selectors {
  return GenericFeedRule(
    linkXPath = linkXPath,
    extendContext = ExtendContext.NONE,
    contextXPath = contextXPath,
    dateXPath = dateXPath,
    paginationXPath = paginationXPath,
    dateIsStartOfEvent = dateIsStartOfEvent,
    count = 0,
    score = 0.0,
  )
}
