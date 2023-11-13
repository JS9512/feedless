package org.migor.feedless.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor
import org.migor.feedless.AppMetrics
import org.migor.feedless.api.graphql.DtoResolver
import org.migor.feedless.feed.discovery.GenericFeedLocator
import org.migor.feedless.feed.discovery.NativeFeedLocator
import org.migor.feedless.feed.discovery.TransientNativeFeed
import org.migor.feedless.feed.discovery.TransientOrExistingNativeFeed
import org.migor.feedless.feed.parser.FeedType
import org.migor.feedless.generated.types.DOMElementByXPath
import org.migor.feedless.generated.types.EmittedScrapeData
import org.migor.feedless.generated.types.Fragment
import org.migor.feedless.generated.types.ScrapeDebugResponse
import org.migor.feedless.generated.types.ScrapeDebugTimes
import org.migor.feedless.generated.types.ScrapeEmitType
import org.migor.feedless.generated.types.ScrapeRequest
import org.migor.feedless.generated.types.ScrapeResponse
import org.migor.feedless.generated.types.ScrapedElement
import org.migor.feedless.generated.types.ScrapedFeeds
import org.migor.feedless.generated.types.ScrapedReadability
import org.migor.feedless.harvest.HarvestResponse
import org.migor.feedless.util.FeedUtil
import org.migor.feedless.util.GenericFeedUtil
import org.migor.feedless.util.HtmlUtil
import org.migor.feedless.web.GenericFeedParserOptions
import org.migor.feedless.web.GenericFeedRule
import org.migor.feedless.web.WebToArticleTransformer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import us.codecraft.xsoup.Xsoup
import java.nio.charset.Charset


@Service
class ScrapeService {

  private val log = LoggerFactory.getLogger(HttpService::class.simpleName)

  @Autowired
  lateinit var httpService: HttpService

  @Autowired
  lateinit var nativeFeedLocator: NativeFeedLocator

  @Autowired
  lateinit var genericFeedLocator: GenericFeedLocator

  @Autowired
  lateinit var webToArticleTransformer: WebToArticleTransformer

  @Autowired
  lateinit var puppeteerService: PuppeteerService

  @Autowired
  lateinit var feedParserService: FeedParserService

  @Autowired
  lateinit var meterRegistry: MeterRegistry

  fun scrape(corrId: String, scrapeRequest: ScrapeRequest): Mono<ScrapeResponse> {
    val prerender = scrapeRequest.page.prerender != null

    if (scrapeRequest.emit.any { scrapeEmit -> scrapeEmit.fragment.xpath == null && scrapeEmit.fragment.boundingBox == null }) {
      throw IllegalArgumentException("[${corrId}] fragment is underspecified.")
    }

    if (!prerender && scrapeRequest.emit.any { scrapeEmit -> scrapeEmit.types.contains(ScrapeEmitType.pixel) }) {
      throw IllegalArgumentException("[${corrId}] emitting pixel requires preprendering")
    }

    meterRegistry.counter(
      AppMetrics.scrape, listOf(
        Tag.of("type", "scrape"),
        Tag.of("prerender", prerender.toString()),
      )
    ).increment()

    return if (prerender) {
      log.info("[$corrId] prerender")
      puppeteerService.prerender(corrId, scrapeRequest)
        .map { injectScrapeData(corrId, scrapeRequest, it) }
    } else {
      log.info("[$corrId] static")
      val startTime = System.nanoTime()
      val url = scrapeRequest.page.url
      httpService.guardedHttpResource(
        corrId,
        url,
        200,
        listOf("text/", "application/xml", "application/json", "application/rss", "application/atom", "application/rdf")
      )
      val staticResponse = httpService.httpGetCaching(corrId, url, 200)
      val document = Jsoup.parse(String(staticResponse.responseBody))
      val (feedType, mimeType) = FeedUtil.detectFeedTypeForResponse(staticResponse)

      val builder = ScrapeResponse.newBuilder()
        .failed(false)
        .url(scrapeRequest.page.url)
        .debug(
          ScrapeDebugResponse.newBuilder()
            .corrId(corrId)
            .console(emptyList())
            .html(staticResponse.responseBody.toString(Charset.defaultCharset()))
            .contentType(mimeType)
            .statusCode(staticResponse.statusCode)
            .cookies(emptyList())
            .network(emptyList())
            .metrics(ScrapeDebugTimes.newBuilder()
              .render(System.nanoTime().minus(startTime).div(1000000).toInt())
              .queue(0)
              .build())
            .build()
        )

      if (feedType !== FeedType.NONE) {
        val feed = feedParserService.parseFeed(corrId, HarvestResponse(url, staticResponse))
        log.debug("[$corrId] is native-feed")

        builder
          .elements(
            listOf(
              ScrapedElement.newBuilder()
                .fragment(Fragment.newBuilder()
                  .xpath(DOMElementByXPath.newBuilder()
                    .value("/")
                    .build())
                  .build())
                .data(
                  listOf(
                    EmittedScrapeData.newBuilder()
                      .type(ScrapeEmitType.markup)
                      .markup(staticResponse.responseBody.toString())
                      .build(),
                    EmittedScrapeData.newBuilder()
                      .type(ScrapeEmitType.feeds)
                      .feeds(
                        ScrapedFeeds.newBuilder()
                          .genericFeeds(emptyList())
                          .nativeFeeds(
                            listOf(
                              DtoResolver.toDto(
                                TransientOrExistingNativeFeed(
                                  transient = TransientNativeFeed(
                                    url = url,
                                    type = feedType,
                                    title = feed.title,
                                    description = feed.description
                                  )
                                )
                              )
                            )
                          )
                          .build()
                      )
                      .build()
                  )
                )
                .build()
            )
          )
          .build()
      } else {
        val elements = scrapeRequest.emit
          .map { scrapeEmit -> run {

            scrapeEmit.fragment.boundingBox?.let {
              throw IllegalArgumentException("fragment spec of type boundingBox requires prepredering")
            }

            val xpath = scrapeEmit.fragment.xpath.value

            val fragment = StringUtils.trimToNull(xpath)?.let {
              Xsoup.compile(xpath).evaluate(document).elements.firstOrNull()
                ?: throw IllegalArgumentException("xpath $xpath cannot be resolved")
            } ?: document

            val scrapedData = scrapeEmit.types.map {
                when (it) {
                ScrapeEmitType.text -> run {
                  val texts = mutableListOf<String>()
                  fragment.traverse(textElements(texts))
                  toEmittedData(it, text = texts.joinToString("\n"))
                }

                ScrapeEmitType.markup -> toEmittedData(it, markup = fragment.html())
                ScrapeEmitType.feeds -> toEmittedData(it, feeds = toScrapedFeeds(corrId, fragment.html(), url))
                ScrapeEmitType.readability -> toEmittedData(it, readability = toScrapedReadability(corrId, fragment.html(), url))
                else -> throw IllegalArgumentException("pixel cannot be extracted in static mode")
              }

              }

            ScrapedElement.newBuilder()
              .fragment(Fragment.newBuilder()
                .xpath(DOMElementByXPath.newBuilder()
                  .value("/")
                  .build())
                .build()
              )
              .data(scrapedData)
              .build()

            }
          }
//          .map { xpath ->
//          run {
//            val fragment = StringUtils.trimToNull(xpath)?.let {
//              Xsoup.compile(xpath).evaluate(document).elements.firstOrNull()
//                ?: throw IllegalArgumentException("xpath $xpath cannot be resolved")
//            } ?: document
//
//            val scrapedData = scrapeRequest.emit.map {
//              when (it) {
//                ScrapeEmitType.text -> run {
//                  val texts = mutableListOf<String>()
//                  fragment.traverse(textElements(texts))
//                  toEmittedData(it, text = texts.joinToString("\n"))
//                }
//
//                ScrapeEmitType.markup -> toEmittedData(it, markup = fragment.html())
//                ScrapeEmitType.feeds -> toEmittedData(it, feeds = toScrapedFeeds(corrId, fragment.html(), url))
//                ScrapeEmitType.readability -> toEmittedData(it, readability = toScrapedReadability(corrId, fragment.html(), url))
//                else -> throw IllegalArgumentException("pixel cannot be extracted in static mode")
//              }
//            }
//
//            ScrapedElement.newBuilder()
//              .fragment(Fragment.newBuilder()
//                .xpath(DOMElementByXPath.newBuilder()
//                  .value("/")
//                  .build())
//                .build()
//              )
//              .data(scrapedData)
//              .build()
//          }
//        }
        builder.elements(elements)
      }

      Mono.just(builder.build())
    }
  }

  private fun injectScrapeData(corrId: String, req: ScrapeRequest, res: ScrapeResponse): ScrapeResponse {
    return ScrapeResponse.newBuilder()
      .failed(res.failed)
      .url(res.url)
      .errorMessage(res.errorMessage)
      .debug(res.debug)
      .elements(res.elements.map { injectFeedsAndReadability(corrId, req, it) })
      .build()
  }

  private fun toScrapedFeeds(
    corrId: String,
    markup: String,
    url: String,
  ): ScrapedFeeds {
    val document = HtmlUtil.parseHtml(markup, url)
    val (nativeFeeds, genericFeeds) = extractFeeds(corrId, document, url, false)
    return ScrapedFeeds.newBuilder()
      .genericFeeds(genericFeeds.map { GenericFeedUtil.toDto(it) })
      .nativeFeeds(nativeFeeds.map { DtoResolver.toDto(it) })
      .build()
  }

  private fun toScrapedReadability(
    corrId: String,
    markup: String,
    url: String,
  ): ScrapedReadability {
    val article = webToArticleTransformer.fromHtml(markup, url)
    return ScrapedReadability.newBuilder()
      .date(article.date)
      .content(article.content)
      .url(article.url)
      .contentText(article.contentText)
      .contentMime(article.contentMime)
      .faviconUrl(article.faviconUrl)
      .imageUrl(article.imageUrl)
      .title(article.title)
      .build()
  }

  private fun injectFeedsAndReadability(
    corrId: String,
    req: ScrapeRequest,
    element: ScrapedElement
  ): ScrapedElement {

    val listOfData = element.data.toMutableList()
    val url = req.page.url

    val emitTypes = req.emit.flatMap { scrapeEmit -> scrapeEmit.types }
    val emitFeeds = emitTypes.contains(ScrapeEmitType.feeds)
    val emitReadability = emitTypes.contains(ScrapeEmitType.feeds)

    if (emitFeeds || emitReadability) {
      val markup = element.data.find { StringUtils.isNotBlank(it.markup) }!!.markup

      if (emitFeeds) {
        listOfData.add(EmittedScrapeData.newBuilder()
          .type(ScrapeEmitType.feeds)
          .feeds(toScrapedFeeds(corrId, markup, url))
          .build()
        )
      }
      if (emitReadability) {
        listOfData.add(EmittedScrapeData.newBuilder()
          .type(ScrapeEmitType.readability)
          .readability(toScrapedReadability(corrId, markup, url))
          .build()
        )
      }
    }

    return ScrapedElement.newBuilder()
      .fragment(element.fragment)
      .data(listOfData)
      .build()
  }

  private fun extractFeeds(
    corrId: String,
    document: Document,
    url: String,
    strictMode: Boolean
  ): Pair<List<TransientOrExistingNativeFeed>, List<GenericFeedRule>> {
    val parserOptions = GenericFeedParserOptions(
      strictMode = strictMode
    )
    val genericFeedRules = genericFeedLocator.locateInDocument(corrId, document, url, parserOptions)
    val nativeFeeds = nativeFeedLocator.locateInDocument(document, url)
    log.info("[$corrId] Found feedRules=${genericFeedRules.size} nativeFeeds=${nativeFeeds.size}")
    return Pair(nativeFeeds, genericFeedRules)
  }


  private fun toEmittedData(scrapeEmitType: ScrapeEmitType, text: String? = null, markup: String? = null, feeds: ScrapedFeeds? = null, readability: ScrapedReadability? = null): EmittedScrapeData {
    return EmittedScrapeData.newBuilder()
      .type(scrapeEmitType)
      .text(text)
      .markup(markup)
      .feeds(feeds)
      .readability(readability)
      .build()
  }

  private fun textElements(texts: MutableList<String>): NodeVisitor {
    return object : NodeVisitor {
      override fun head(node: Node, depth: Int) {
        if (node is TextNode) {
          texts.add(node.text())
        }
      }

      override fun tail(node: Node, depth: Int) {
      }
    }
  }
}

fun ScrapeResponse.getRootElement(): ScrapedElement {
  return this.elements.minByOrNull { it.fragment?.xpath?.value?.length ?: 0 } ?: throw IllegalArgumentException("no root element present")
}
