package org.migor.feedless.trigger.plugins

import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.migor.feedless.AppProfiles
import org.migor.feedless.data.jpa.models.FeatureState
import org.migor.feedless.data.jpa.models.WebDocumentEntity
import org.migor.feedless.data.jpa.repositories.WebDocumentDAO
import org.migor.feedless.generated.types.ScrapeEmitType
import org.migor.feedless.generated.types.ScrapePage
import org.migor.feedless.generated.types.ScrapePrerender
import org.migor.feedless.generated.types.ScrapeRequest
import org.migor.feedless.generated.types.ScrapeResponse
import org.migor.feedless.harvest.HarvestAbortedException
import org.migor.feedless.service.HttpResponse
import org.migor.feedless.service.HttpService
import org.migor.feedless.service.PdfService
import org.migor.feedless.service.ScrapeService
import org.migor.feedless.service.getRootElement
import org.migor.feedless.util.HtmlUtil
import org.migor.feedless.web.ExtractedArticle
import org.migor.feedless.web.WebToArticleTransformer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.util.MimeType
import org.springframework.util.ResourceUtils.isUrl
import java.nio.charset.Charset
import java.util.*

@Service
@Profile(AppProfiles.database)
class FulltextPlugin : WebDocumentPlugin {

  private val log = LoggerFactory.getLogger(FulltextPlugin::class.simpleName)

  @Autowired
  lateinit var httpService: HttpService

  @Autowired
  lateinit var webToArticleTransformer: WebToArticleTransformer

  @Autowired
  lateinit var webDocumentDAO: WebDocumentDAO

  @Autowired
  lateinit var pdfService: PdfService

  @Autowired
  lateinit var scrapeService: ScrapeService

  override fun id(): String = "fulltext"

  override fun description(): String = "Extracts fulltext-article of a document"

  override fun executionPhase(): PluginPhase = PluginPhase.harvest
  override fun state(): FeatureState = FeatureState.beta
  override fun processWebDocument(corrId: String, webDocument: WebDocumentEntity) {
    val url = webDocument.url

    if (!isUrl(url)) {
      throw HarvestAbortedException("illegal url $url")
    }

    if (isBlacklistedForHarvest(url)) {
      throw HarvestAbortedException("blacklisted $url")
    }

    val canPrerender = arrayOf("text/html", "text/plain").contains(
      httpService.getContentTypeForUrl(
        corrId,
        url
      )
    )

    harvest(corrId, webDocument, false).let {
      if (canPrerender && Jsoup.parse(String(it.responseBody)).select("noscript").isNotEmpty()) {
        log.info("[$corrId] -> prerender (found noscript tag)")
        saveExtractionForContent(corrId, webDocument, it.url, harvest(corrId, webDocument, true))
      } else {
        saveExtractionForContent(corrId, webDocument, it.url, it)
      }
    }
  }

  override fun configurableByUser(): Boolean = true
  override fun configurableInUserProfileOnly(): Boolean = false
  override fun enabled(): Boolean = true

  private fun harvest(
    corrId: String,
    webDocument: WebDocumentEntity,
    shouldPrerender: Boolean = false
  ): HttpResponse {

    val url = webDocument.url

    val scrapeRequest = ScrapeRequest.newBuilder()
      .page(
        ScrapePage.newBuilder()
          .url(url)
          .prerender(
            ScrapePrerender.newBuilder()

              .build()
          )
          .build()
      )
      .build()

    return scrapeService.scrape(corrId, scrapeRequest).block()!!.toHttpResponse()
  }

  companion object {
    fun isBlacklistedForHarvest(url: String): Boolean {
      return listOf("https://twitter.com", "https://www.imdb.com", "https://www.google.").any { url.startsWith(it) }
    }
  }

  private fun extractFromAny(
    corrId: String,
    url: String,
    response: HttpResponse
  ): ExtractedArticle {
    val mime = MimeType.valueOf(response.contentType)
    return when (val contentType = "${mime.type}/${mime.subtype}") {
      "text/html" -> fromMarkup(corrId, url, String(response.responseBody))
      "text/plain" -> fromText(corrId, url, response)
      "application/pdf" -> fromPdf(corrId, url, response)
      else -> {
        log.warn("[${corrId}] Cannot extract article from mime $contentType")
        throw IllegalArgumentException("Unsupported contentType $contentType for extraction")
      }
    }
  }

  private fun fromText(corrId: String, url: String, response: HttpResponse): ExtractedArticle {
    log.info("[${corrId}] from text")
    val extractedArticle = ExtractedArticle(url)
    extractedArticle.contentText = StringUtils.trimToNull(String(response.responseBody))
    return extractedArticle
  }

  private fun fromPdf(corrId: String, url: String, response: HttpResponse): ExtractedArticle {
    log.info("[${corrId}] from pdf")
    return fromMarkup(corrId, url, pdfService.toHTML(corrId, response.responseBody))
  }

  private fun fromMarkup(corrId: String, url: String, markup: String): ExtractedArticle {
    log.info("[${corrId}] from markup")
    return webToArticleTransformer.fromHtml(markup, url)
  }

  private fun saveExtractionForContent(
    corrId: String,
    webDocument: WebDocumentEntity,
    url: String,
    httpResponse: HttpResponse
  ) {
    val extractedArticle = extractFromAny(corrId, url, httpResponse)
    extractedArticle.title?.let {
      log.debug("[$corrId] title ${webDocument.contentTitle} -> $it")
      webDocument.contentTitle = it
    }
    StringUtils.trimToNull(extractedArticle.content)?.let {
      log.debug(
        "[$corrId] contentRawMime ${webDocument.contentRawMime} -> ${
          StringUtils.substring(
            extractedArticle.contentMime,
            0,
            100
          )
        }"
      )

      if (extractedArticle.contentMime!!.startsWith("text/html")) {
        val document = HtmlUtil.parseHtml(it, webDocument.url)
        webDocument.contentRaw = StringUtils.trimToNull(document.body().html())
      } else {
        webDocument.contentRaw = it
      }
      webDocument.contentRawMime = extractedArticle.contentMime!!
    }
    log.debug(
      "[$corrId] mainImageUrl ${webDocument.imageUrl} -> ${
        StringUtils.substring(
          extractedArticle.imageUrl,
          0,
          100
        )
      }"
    )
    webDocument.imageUrl = StringUtils.trimToNull(extractedArticle.imageUrl) ?: webDocument.imageUrl
    webDocument.contentText = StringUtils.trimToNull(extractedArticle.contentText)
//    webDocument.hasFulltext = StringUtils.isNoneBlank(webDocument.contentRaw)
//    log.info("[$corrId] found fulltext ${webDocument.hasFulltext}")
    if (url != webDocument.url) {
      webDocument.aliasUrl = url
    }

    webDocumentDAO.saveFulltextContent(
      webDocument.id,
      webDocument.aliasUrl,
      webDocument.contentTitle,
      webDocument.contentRaw,
      webDocument.contentRawMime,
      webDocument.contentText,
      webDocument.imageUrl,
      Date()
    )

  }
}

fun ScrapeResponse.toHttpResponse(): HttpResponse {
//  assert(this.getRootElement().data.size == 1) { "Only one element selector allowed" }
  val firstEmitted = this.getRootElement().selector
  return HttpResponse(
    contentType = this.debug.contentType,
    url = this.url,
    statusCode = 200,
    responseBody = firstEmitted.html.data.toByteArray(Charset.defaultCharset()),
  )
}
