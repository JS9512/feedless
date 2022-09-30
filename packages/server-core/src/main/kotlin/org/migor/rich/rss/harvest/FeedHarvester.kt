package org.migor.rich.rss.harvest

import org.apache.commons.lang3.StringUtils
import org.migor.rich.rss.api.dto.RichArticle
import org.migor.rich.rss.database2.models.ArticleEntity
import org.migor.rich.rss.database2.models.ArticleType
import org.migor.rich.rss.database2.models.NativeFeedEntity
import org.migor.rich.rss.database2.repositories.ArticleDAO
import org.migor.rich.rss.database2.repositories.SiteHarvestDAO
import org.migor.rich.rss.database2.repositories.UserDAO
import org.migor.rich.rss.service.ArticleService
import org.migor.rich.rss.service.ExporterTargetService
import org.migor.rich.rss.service.FeedService
import org.migor.rich.rss.service.HttpResponse
import org.migor.rich.rss.service.HttpService
import org.migor.rich.rss.service.PropertyService
import org.migor.rich.rss.service.ScoreService
import org.migor.rich.rss.util.CryptUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.*


@Service
@Profile("database2")
class FeedHarvester internal constructor() {

  private val log = LoggerFactory.getLogger(FeedHarvester::class.simpleName)

  @Autowired
  lateinit var articleService: ArticleService

  @Autowired
  lateinit var scoreService: ScoreService

  @Autowired
  lateinit var exporterTargetService: ExporterTargetService

  @Autowired
  lateinit var feedService: FeedService

  @Autowired
  lateinit var propertyService: PropertyService

  @Autowired
  lateinit var httpService: HttpService

  @Autowired
  lateinit var siteHarvestDAO: SiteHarvestDAO

  @Autowired
  lateinit var userDao: UserDAO

  @Autowired
  lateinit var articleDAO: ArticleDAO

  fun harvestFeed(corrId: String, feed: NativeFeedEntity) {
    runCatching {
      this.log.info("[$corrId] Harvesting feed ${feed.id} (${feed.feedUrl})")
      val fetchContext = createFetchContext(corrId, feed)
      val httpResponse = fetchFeed(corrId, fetchContext)
      val parsedFeed = feedService.parseFeed(corrId, HarvestResponse(fetchContext.url, httpResponse))
//      updateFeedMetadata(corrId, parsedFeed, feed)
      handleFeedItems(corrId, feed, parsedFeed.items)
//
//      if (FeedStatus.ok != feed.status) {
//        this.log.debug("[$corrId] status-change for Feed ${feed.feedUrl}: ${feed.status} -> ok")
//        feedService.redeemStatus(feed)
//      }
//      feed.failedAttemptCount = 0

    }.onFailure { ex ->
      run {
        log.error("[$corrId] Harvest failed ${ex.message}")
        ex.printStackTrace()
        feedService.updateNextHarvestDateAfterError(corrId, feed, ex)
      }
    }
  }

  private fun createFetchContext(corrId: String, feed: NativeFeedEntity): FetchContext {
    return FetchContext(feed.feedUrl!!, feed)
  }

  private fun handleFeedItems(
    corrId: String,
    feed: NativeFeedEntity,
    items: List<RichArticle>
  ) {
    val newArticles = items
      .map { item -> saveOrUpdateArticle(corrId, item, feed) }
      .filter { pair: Pair<Boolean, ArticleEntity> -> pair.first }
      .map { pair -> pair.second }


    if (newArticles.isEmpty()) {
      log.debug("[$corrId] Up-to-date ${feed.feedUrl}")
    } else {
      log.info("[$corrId] Appended ${newArticles.size} articles")
      feedService.updateUpdatedAt(corrId, feed)
      feedService.applyRetentionStrategy(corrId, feed)
    }

    val systemUser = userDao.findByName("system")!!
    val stream = feed.stream!!

    // todo mag forward all articles at once
    exporterTargetService.pushArticlesToTargets(
      corrId,
      newArticles,
      stream,
      ArticleType.feed,
      systemUser
    )

    log.info("[${corrId}] Updated feed ${propertyService.publicUrl}/feed:${feed.id}")

    feedService.updateNextHarvestDate(corrId, feed, newArticles.isNotEmpty())
  }

  private fun saveOrUpdateArticle(corrId: String, article: RichArticle, feed: NativeFeedEntity): Pair<Boolean, ArticleEntity> {
    val optionalEntry = Optional.ofNullable(articleDAO.findByUrl(article.url))
    return if (optionalEntry.isPresent) {
      Pair(false, updateArticleProperties(corrId, optionalEntry.get(), article))
    } else {
      Pair(true, articleService.create(corrId, toEntity(article), feed))
    }
  }

  private fun toEntity(article: RichArticle): ArticleEntity {
    val entity = ArticleEntity()
    entity.url = article.url
    entity.title = article.title
    entity.mainImageUrl = StringUtils.trimToNull(article.imageUrl)
    entity.contentText = article.contentText
    entity.publishedAt = article.publishedAt
    entity.updatedAt = article.publishedAt
//    entity.released = false
    return entity
  }

  private fun updateArticleProperties(corrId: String, existingArticle: ArticleEntity, newArticle: RichArticle): ArticleEntity {
    val changedTitle = existingArticle.title.equals(newArticle.title)
    if (changedTitle) {
      existingArticle.title = newArticle.title
    }
    val changedContentText = existingArticle.contentText.equals(newArticle.contentText)
    if (changedContentText) {
      existingArticle.contentText = newArticle.contentText
    }

//    todo mag
//    val allTags = HashSet<NamespacedTag>()
//    newArticle.tags?.let { tags -> allTags.addAll(tags) }
//    existingArticle.tags?.let { tags -> allTags.addAll(tags) }
//    existingArticle.tags = allTags.toList()
    return articleService.update(corrId, existingArticle)
  }

//  private fun saveArticle(syndEntry: RichArticle): Article? {
//    return try {
//      val article = articleDAO.findByUrl(syndEntry)
//      article.url = syndEntry.url
//      article.title = syndEntry.title
//
//      val (text, html) = extractContent(syndEntry)
//      if (StringUtils.isBlank(article.contentRaw)) {
//        html?.let { t ->
//          run {
//            article.contentRaw = t.second
//            article.contentRawMime = t.first.toString()
//          }
//        }
//      }
//      text?.let { t ->
//        run {
//          article.contentText = HtmlUtil.html2text(t.second)
//        }
//      }
//
//      article.author = syndEntry.author
////      val tags = syndEntry.tags.toMutableSet()
////      if (syndEntry.enclosures != null && syndEntry.enclosures.isNotEmpty()) {
// todo mag tags
////        tags.addAll(
////          syndEntry.enclosures
////            .map { enclusure ->
////              NamespacedTag(
////                TagNamespace.CONTENT,
////                MimeType(enclusure.type).type.lowercase(Locale.getDefault())
////              )
////            }
////        )
////      }
////      todo mag support article.enclosures = JsonUtil.gson.toJson(syndEntry.enclosures)
////      article.putDynamicField("", "enclosures", syndEntry.enclosures)
////      article.tags = tags.toList()
////      article.commentsFeedUrl = syndEntry.comments
////    todo mag add feedUrl as featured link
////      article.sourceUrl = feed.feedUrl
//      article.released = !feed.harvestSite
//
//      article.pubDate = Optional.ofNullable(syndEntry.publishedAt).orElse(Date())
//      article.createdAt = Date()
//      article
//    } catch (e: Exception) {
//      null
//    }
//  }

//  private fun extractContent(article: RichArticle): Pair<Pair<MimeType, String>?, Pair<MimeType, String>?> {
//    val contents = ArrayList<Pair<String,String>>()
//    contents.add(Pair("text/plain", article.contentText))
//    article.contentRaw?.let {
//      contents.add(Pair(article.contentRawMime!!, it))
//    }
//    val html = contents.find { (mime) ->
//      mime.lowercase(Locale.getDefault()).endsWith("html")
//    }?.let { htmlContent -> Pair(MimeType.valueOf("text/html"), htmlContent.second) }
//    val text = if (contents.isNotEmpty()) {
//      if (html == null) {
//        Pair(MimeType.valueOf("text/plain"), contents.first().second)
//      } else {
//        Pair(MimeType.valueOf("text/plain"), HtmlUtil.html2text(html.second))
//      }
//    } else {
//      null
//    }
//    return Pair(text, html)
//  }

  private fun fetchFeed(corrId: String, context: FetchContext): HttpResponse {
    val branchedCorrId = CryptUtil.newCorrId(parentCorrId = corrId)
    val request = httpService.prepareGet(context.url)
    log.info("[$branchedCorrId] GET ${context.url}")
    return httpService.executeRequest(branchedCorrId, request, context.expectedStatusCode)
  }
}

data class FetchContext(
  val url: String,
  val feed: NativeFeedEntity,
  val expectedStatusCode: Int = 200
)