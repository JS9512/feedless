package org.migor.rss.rich.database.model

import org.hibernate.annotations.GenericGenerator
import org.migor.rss.rich.api.dto.ArticleJsonDto
import org.migor.rss.rich.generated.MqReadabilityData
import org.migor.rss.rich.service.ArticleService
import org.migor.rss.rich.util.JsonUtil
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.CreatedDate
import java.util.*
import javax.persistence.*
import javax.validation.constraints.NotNull


@Entity
@Table(name = "Article")
class Article {
  @Transient
  private val log = LoggerFactory.getLogger(Article::class.simpleName)

  fun linkCount(): Int {
    val linkCount = ArticleService.getLinkCount(this)
    log.info("article ${url} has linkCount ${linkCount}")
    return linkCount
  }

  fun toDto(): ArticleJsonDto {
    return ArticleJsonDto(
      id = this.id!!,
      title = this.title!!,
      url = this.url!!,
      author = this.author,
      tags = this.tags?.map { tag -> "${tag.namespace}:${tag.tag}" },
      enclosures = this.enclosures,
      commentsFeedUrl = this.commentsFeedUrl,
      content_text = this.content,
      content_html = this.contentHtml,
      date_published = this.pubDate
    )
  }

  @Id
  @GeneratedValue(generator = "uuid")
  @GenericGenerator(name = "uuid", strategy = "uuid2")
  var id: String? = null

  @NotNull
  @Column(name = "title")
  var title: String? = null

  @Column(name = "url", columnDefinition = "TEXT")
  var url: String? = null

  @Column(name = "readability", columnDefinition = "JSON")
  var readabilityJson: String? = null

  @Transient
  var readability: MqReadabilityData? = null

  @Column(name = "has_readability")
  var hasReadability: Boolean? = null

  @Column(name = "author")
  var author: String? = null

  @Column(name = "source_url")
  var sourceUrl: String? = null

  @Column(name = "applyPostProcessors")
  var applyPostProcessors: Boolean = true

  @Column(name = "released")
  var released: Boolean = true

  @Column(name = "tags", columnDefinition = "JSON")
  var tagsJson: String? = null

  @Transient
  var tags: List<NamespacedTag>? = null

  @Column(name = "enclosure", columnDefinition = "JSON")
  var enclosures: String? = null

  @Column(name = "comment_feed_url")
  var commentsFeedUrl: String? = null

  @Column(name = "content_html", columnDefinition = "LONGTEXT")
  var contentHtml: String? = null

  @NotNull
  @Column(name = "content_text", columnDefinition = "LONGTEXT")
  var content: String = ""

  @NotNull
  @Column(name = "score")
  var score: Double = 0.0

  @NotNull
  @Temporal(TemporalType.TIMESTAMP)
  var lastScoredAt: Date = Date()

  @CreatedDate
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "createdAt")
  var createdAt = Date()

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "date_published")
  var pubDate = Date()

  @PrePersist
  @PreUpdate
  fun prePersist() {
    if (title != null && title!!.length > 200) {
      title = title?.substring(0, 197) + "..."
    }
    readability?.let {
      readabilityJson = JsonUtil.gson.toJson(readability)
    }
    tags?.let {
      tagsJson = JsonUtil.gson.toJson(tags)
    }
  }

  @PostLoad
  fun postLoad() {
    readabilityJson?.let {
      readability = JsonUtil.gson.fromJson(readabilityJson, MqReadabilityData::class.java)
    }
    tagsJson?.let {
      tags = JsonUtil.gson.fromJson<List<NamespacedTag>>(tagsJson, List::class.java)
    }
  }
}