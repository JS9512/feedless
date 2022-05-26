package org.migor.rich.rss.api

import org.migor.rich.rss.api.dto.ArticleJsonDto
import org.migor.rich.rss.service.BucketService
import org.migor.rich.rss.util.CryptUtil.handleCorrId
import org.migor.rich.rss.util.CryptUtil.newCorrId
import org.migor.rich.rss.util.FeedExporter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@Controller
@Profile("stateful")
class BucketController {

  @Autowired
  lateinit var bucketService: BucketService

  @GetMapping("/bucket:{bucketId}/rss", produces = ["application/rss+xml;charset=UTF-8"])
  fun rssFeed(
    @PathVariable("bucketId") bucketId: String,
    @PathVariable("type", required = false) type: String?,
    @RequestParam("page", required = false, defaultValue = "0") page: Int
  ): ResponseEntity<String> {
    return FeedExporter.toRss(newCorrId(), bucketService.findByBucketId(bucketId, page, type))
  }

  @GetMapping("/bucket:{bucketId}", "/bucket:{bucketId}/atom", produces = ["application/atom+xml;charset=UTF-8"])
  fun atomFeed(
    @PathVariable("bucketId") bucketId: String,
    @PathVariable("type", required = false) type: String?,
    @RequestParam("page", required = false, defaultValue = "0") page: Int
  ): ResponseEntity<String> {
    return FeedExporter.toAtom(newCorrId(), bucketService.findByBucketId(bucketId, page, type))
  }

  @GetMapping("/bucket:{bucketId}/json", produces = ["application/json;charset=UTF-8"])
  fun jsonFeed(
    @PathVariable("bucketId") bucketId: String,
    @PathVariable("type", required = false) type: String?,
    @RequestParam("page", required = false, defaultValue = "0") page: Int
  ): ResponseEntity<String> {
    return FeedExporter.toJson(newCorrId(), bucketService.findByBucketId(bucketId, page, type))
  }

  @PutMapping("/bucket:{bucketId}/put")
  fun addToBucket(
    @RequestParam("correlationId", required = false) correlationId: String?,
    @PathVariable("bucketId") bucketId: String,
    @RequestParam("opSecret") feedsOpSecret: String,
    @RequestBody article: ArticleJsonDto
  ) {
    return bucketService.addToBucket(handleCorrId(correlationId), bucketId, article, feedsOpSecret)
  }

  @DeleteMapping("/bucket:{bucketId}/delete")
  fun deleteFromBucket(
    @RequestParam("correlationId", required = false) correlationId: String?,
    @PathVariable("bucketId") bucketId: String,
    @RequestParam("articleId") articleId: String,
    @RequestParam("opSecret") feedsOpSecret: String
  ) {
    return bucketService.deleteFromBucket(handleCorrId(correlationId), bucketId, articleId, feedsOpSecret)
  }
}
