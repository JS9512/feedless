package org.migor.rich.rss.service

import org.migor.rich.rss.database.enums.ArticleType
import org.migor.rich.rss.database.enums.ImporterTargetType
import org.migor.rich.rss.database.enums.ReleaseStatus
import org.migor.rich.rss.database.models.ArticleContentEntity
import org.migor.rich.rss.database.models.NativeFeedEntity
import org.migor.rich.rss.database.models.ArticleEntity
import org.migor.rich.rss.database.models.StreamEntity
import org.migor.rich.rss.database.repositories.ArticleContentDAO
import org.migor.rich.rss.database.repositories.ArticleDAO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Profile("database")
class ImporterService {

  private val log = LoggerFactory.getLogger(ImporterService::class.simpleName)

  @Autowired
  lateinit var httpService: HttpService

  @Autowired
  lateinit var articleDAO: ArticleDAO

  @Autowired
  lateinit var contentDAO: ArticleContentDAO

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun importArticlesToTargets(
    corrId: String,
    contents: List<ArticleContentEntity>,
    stream: StreamEntity,
    feed: NativeFeedEntity,
    articleType: ArticleType,
    status: ReleaseStatus,
    overwritePubDate: Date? = null,
    targets: Array<ImporterTargetType> = emptyArray()
  ) {
    contents.forEach { content ->
      importArticleToTargets(
        corrId,
        content,
        stream,
        feed,
        articleType,
        Optional.ofNullable(overwritePubDate).orElse(content.publishedAt!!),
        targets
      )
    }
  }

  private fun importArticleToTargets(
    corrId: String,
    content: ArticleContentEntity,
    stream: StreamEntity,
    feed: NativeFeedEntity,
    articleType: ArticleType,
    pubDate: Date,
    targets: Array<ImporterTargetType>,
  ) {
    val articleId = content.id
    Optional.ofNullable(contentDAO.findInStream(articleId, stream.id))
      .ifPresentOrElse({
        log.warn("[${corrId}] already exported")
      }, {
        log.info("[$corrId] exporting article $articleId")

        // default target
        forwardToStream(corrId, content, pubDate, stream, feed, articleType)

        targets.forEach { target ->
          when (target) {
//            ExporterTargetType.push -> forwardAsPush(corrId, articleId, ownerId, pubDate, refType)
//            ExporterTargetType.email -> forwardAsEmail(corrId, articleId, ownerId, pubDate, refType)
//            ExporterTargetType.webhook -> forwardToWebhook(corrId, article, pubDate, target)
            else -> log.warn("[${corrId}] Unsupported exporterTarget $target")
          }
        }
      })

  }

//  private fun forwardAsEmail(
//    corrId: String,
//    articleId: UUID,
//    ownerId: UUID,
//    pubDate: Date,
//    refType: Stream2ArticleEntityType
//  ) {
//    TODO("Not yet implemented")
//  }

//  private fun forwardAsPush(
//    corrId: String,
//    articleId: UUID,
//    ownerId: UUID,
//    pubDate: Date,
//    refType: Stream2ArticleEntityType
//  ) {
//    log.info("[$corrId] push article -> owner $ownerId")
//  }

  private fun forwardToStream(
    corrId: String,
    content: ArticleContentEntity,
    pubDate: Date,
    stream: StreamEntity,
    feed: NativeFeedEntity,
    type: ArticleType
  ) {
    log.debug("[$corrId] append article -> stream $stream")
    val link = ArticleEntity()
    link.content = content
    link.releasedAt = pubDate
    link.stream = stream
    link.type = type
    link.status = ReleaseStatus.released
    link.feed = feed
    articleDAO.save(link)
  }
}