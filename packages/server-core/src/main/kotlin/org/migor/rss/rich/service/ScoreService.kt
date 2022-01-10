package org.migor.rss.rich.service

import org.migor.rich.mq.generated.MqAskArticleScore
import org.migor.rss.rich.config.RabbitQueue
import org.migor.rss.rich.database.model.Article
import org.migor.rss.rich.database.model.Feed
import org.migor.rss.rich.database.repository.ArticleRepository
import org.migor.rss.rich.util.JsonUtil
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ScoreService {

  private val log = LoggerFactory.getLogger(ScoreService::class.simpleName)

  @Autowired
  lateinit var rabbitTemplate: RabbitTemplate

  @Autowired
  lateinit var articleRepository: ArticleRepository

  fun askForScoring(corrId: String, article: Article, feed: Feed) {
    val askScore = MqAskArticleScore.Builder()
      .setArticleUrl(article.url!!)
      .setFeedId(feed.id!!)
      .setCorrelationId(corrId)
      .build()
    rabbitTemplate.convertAndSend(RabbitQueue.askArticleScore, JsonUtil.gson.toJson(askScore))
  }

//  @RabbitListener(queues = [RabbitQueue.askArticleScore])
//  fun scoreStatic(articleScoreJson: String) {
//    val request = JsonUtil.gson.fromJson(articleScoreJson, MqAskArticleScore::class.java)
//    val articleUrl = request.articleUrl
//    val article = Optional.ofNullable(articleRepository.findByUrl(articleUrl!!))
//      .orElseThrow { throw IllegalArgumentException("Article ${articleUrl} not found") }
//
//  }
}
