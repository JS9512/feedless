package org.migor.feedless.api.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.coroutineScope
import org.migor.feedless.AppProfiles
import org.migor.feedless.api.ApiParams
import org.migor.feedless.api.Throttled
import org.migor.feedless.api.auth.CookieProvider
import org.migor.feedless.api.auth.CurrentUser
import org.migor.feedless.data.jpa.enums.fromDto
import org.migor.feedless.data.jpa.models.AgentEntity
import org.migor.feedless.data.jpa.models.toDto
import org.migor.feedless.generated.types.*
import org.migor.feedless.plugins.FeedlessPlugin
import org.migor.feedless.plugins.FragmentTransformerPlugin
import org.migor.feedless.service.AgentService
import org.migor.feedless.service.PlanService
import org.migor.feedless.service.PluginService
import org.migor.feedless.service.PropertyService
import org.migor.feedless.service.SourceSubscriptionService
import org.migor.feedless.service.WebDocumentService
import org.migor.feedless.util.CryptUtil.newCorrId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.context.request.ServletWebRequest
import java.util.*

@DgsComponent
@org.springframework.context.annotation.Profile(AppProfiles.database)
class QueryResolver {

  private val log = LoggerFactory.getLogger(QueryResolver::class.simpleName)
  private val pageSize = 20

  @Autowired
  lateinit var currentUser: CurrentUser

  @Autowired
  lateinit var agentService: AgentService

  @Autowired
  lateinit var pluginsService: PluginService

  @Autowired
  lateinit var environment: Environment

  @Autowired
  lateinit var cookieProvider: CookieProvider

  @Autowired
  lateinit var sourceSubscriptionService: SourceSubscriptionService

  @Autowired
  lateinit var propertyService: PropertyService

  @Autowired
  lateinit var webDocumentService: WebDocumentService

  @Autowired
  lateinit var planService: PlanService

  @Throttled
  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun sourceSubscriptions(
    @InputArgument data: SourceSubscriptionsInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<SourceSubscription> = coroutineScope {
    log.info("[$corrId] sourceSubscriptions $data")
    val pageNumber = handlePageNumber(data.cursor.page)
    val pageSize = handlePageSize(data.cursor.pageSize)
    val offset = pageNumber * pageSize
    sourceSubscriptionService.findAll(offset, pageSize, currentUser.userId())
      .map { it.toDto() }
  }

  @Throttled
  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun sourceSubscription(
    @InputArgument data: SourceSubscriptionWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): SourceSubscription = coroutineScope {
    log.info("[$corrId] sourceSubscription $data")
    sourceSubscriptionService.findById(corrId, data.where.id).toDto()
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED)
  suspend fun profile(dfe: DataFetchingEnvironment): Profile = coroutineScope {
    unsetSessionCookie(dfe)
    val defaultProfile = Profile.newBuilder()
      .isLoggedIn(false)
      .isAnonymous(true)
      .dateFormat(propertyService.dateFormat)
      .timeFormat(propertyService.timeFormat)
//      .minimalFeatureState(FeatureState.experimental)
      .build()

    if (currentUser.isUser()) {
      runCatching {
        val user = currentUser.user(newCorrId())
        Profile.newBuilder()
          .dateFormat(propertyService.dateFormat)
          .timeFormat(propertyService.timeFormat)
          .isLoggedIn(true)
          .isAnonymous(false)
          .userId(user.id.toString())
//          .minimalFeatureState(FeatureState.experimental)
          .build()
      }.getOrDefault(defaultProfile)
    } else {
      defaultProfile

    }
  }

  private fun unsetSessionCookie(dfe: DataFetchingEnvironment) {
    val cookie = cookieProvider.createExpiredSessionCookie("JSESSION")
    ((DgsContext.getRequestData(dfe)!! as DgsWebMvcRequestData).webRequest!! as ServletWebRequest).response!!.addCookie(cookie)
  }

  @Throttled
  @DgsQuery
  @PreAuthorize("hasAuthority('USER')")
  suspend fun agents(
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<Agent> = coroutineScope {
    log.info("[$corrId] agents")
    agentService.findAll(currentUser.userId()!!).map { it.toDto() }
  }

  @Throttled
  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun plugins(
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<Plugin> = coroutineScope {
    log.info("[$corrId] plugins")
    pluginsService.findAll().map { it.toDto() }
  }

  @Throttled
  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun webDocument(
    @InputArgument data: WebDocumentWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): WebDocument = coroutineScope {
    log.info("[$corrId] webDocument $data")
    webDocumentService.findById(UUID.fromString(data.where.id))
      .orElseThrow { IllegalArgumentException("webDocument not found")}.toDto()
  }

  @Throttled
  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun webDocuments(
    @InputArgument data: WebDocumentsInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<WebDocument> = coroutineScope {
    log.info("[$corrId] webDocuments $data")
    val subscriptionId = UUID.fromString(data.where.sourceSubscription.where.id)
    webDocumentService.findAllBySubscriptionId(subscriptionId, data.cursor?.page).map { it.toDto() }
  }

  private fun handlePageNumber(page: Int?): Int =
    page ?: 0

  private fun handlePageSize(pageSize: Int?): Int =
    (pageSize ?: this.pageSize).coerceAtLeast(1).coerceAtMost(this.pageSize)

  @Throttled
  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun plans(@RequestHeader(ApiParams.corrId) corrId: String, @InputArgument product: ProductName): List<Plan> = coroutineScope {
    log.info("[$corrId] plans for $product")
    planService.findAllAvailable(product.fromDto()).map { it.toDto() }
  }
}
private fun AgentEntity.toDto(): Agent {
  return Agent.newBuilder()
    .ownerId(ownerId.toString())
    .addedAt(createdAt.time)
    .version(version)
    .openInstance(openInstance)
    .secretKeyId(secretKeyId.toString())
    .build()
}

private fun FeedlessPlugin.toDto(): Plugin {
  return Plugin.newBuilder()
    .id(id())
    .name(name())
    .listed(listed())
    .description(description())
    .type(
      if (this is FragmentTransformerPlugin) {
        PluginType.fragment
      } else {
        PluginType.entity
      }
    )
    .build()
}
