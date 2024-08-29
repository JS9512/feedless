package org.migor.feedless.data.jpa

import jakarta.annotation.PostConstruct
import org.migor.feedless.AppProfiles
import org.migor.feedless.BadRequestException
import org.migor.feedless.common.PropertyService
import org.migor.feedless.data.jpa.enums.EntityVisibility
import org.migor.feedless.data.jpa.enums.ProductCategory
import org.migor.feedless.data.jpa.enums.ReleaseStatus
import org.migor.feedless.document.DocumentDAO
import org.migor.feedless.document.DocumentEntity
import org.migor.feedless.feed.LegacyFeedService
import org.migor.feedless.plan.FeatureGroupDAO
import org.migor.feedless.plan.FeatureGroupEntity
import org.migor.feedless.plan.FeatureName
import org.migor.feedless.plan.FeatureService
import org.migor.feedless.plan.FeatureValueEntity
import org.migor.feedless.plan.FeatureValueType
import org.migor.feedless.plan.PricedProductDAO
import org.migor.feedless.plan.PricedProductEntity
import org.migor.feedless.plan.ProductDAO
import org.migor.feedless.plan.ProductEntity
import org.migor.feedless.repository.RepositoryDAO
import org.migor.feedless.repository.RepositoryEntity
import org.migor.feedless.repository.RepositoryService
import org.migor.feedless.secrets.UserSecretDAO
import org.migor.feedless.secrets.UserSecretEntity
import org.migor.feedless.secrets.UserSecretType
import org.migor.feedless.user.UserDAO
import org.migor.feedless.user.UserEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.floor

@Service
@Order(1)
@Profile("${AppProfiles.seed} & ${AppProfiles.database}")
class Seeder {

  private val log = LoggerFactory.getLogger(Seeder::class.simpleName)

  @Autowired
  private lateinit var featureGroupDAO: FeatureGroupDAO

  @Autowired
  private lateinit var featureService: FeatureService

  @Autowired
  private lateinit var documentDAO: DocumentDAO

  @Autowired
  private lateinit var environment: Environment

  @Autowired
  private lateinit var propertyService: PropertyService

  @Autowired
  private lateinit var productDAO: ProductDAO

  @Autowired
  private lateinit var pricedProductDAO: PricedProductDAO

  @Autowired
  private lateinit var userSecretDAO: UserSecretDAO

  @Autowired
  private lateinit var repositoryDAO: RepositoryDAO

  @Autowired
  private lateinit var repositoryService: RepositoryService

  @Autowired
  private lateinit var legacyFeedService: LegacyFeedService

  @Autowired
  private lateinit var userDAO: UserDAO

  @PostConstruct
  @Transactional(propagation = Propagation.REQUIRED)
  fun onInit() {
    val root = seedRootUser()
    seedProducts(root)
    seedUsers()
  }

  private fun seedUsers() {
    userDAO.findByEmail(propertyService.anonymousEmail) ?: createAnonymousUser()
  }

  private fun seedRootUser(): UserEntity {
    val root =
      userDAO.findFirstByRootIsTrue() ?: createUser(
        propertyService.rootEmail,
        isRoot = true,
      )
    if (root.email != propertyService.rootEmail) {
      log.info("Updated rootEmail")
      root.email = propertyService.rootEmail
      userDAO.save(root)
    }

    if (!userSecretDAO.existsByValueAndOwnerId(propertyService.rootSecretKey, root.id)) {
      log.info("created secretKey for root")
      val userSecret = UserSecretEntity()
      userSecret.ownerId = root.id
      userSecret.value = propertyService.rootSecretKey
      userSecret.type = UserSecretType.SecretKey
      userSecret.validUntil =
        Date.from(LocalDateTime.now().plus(Duration.ofDays(356)).atZone(ZoneId.systemDefault()).toInstant())
      userSecretDAO.save(userSecret)
    }

    pushLegacyNotifications(root)
    pushFeedlessOpsNotifications(root)

    return root
  }

  private fun pushFeedlessOpsNotifications(root: UserEntity) {
    val feedlessOpsNotificationRepo = resolveFeedlessOpsNotificationsRepo(root)

    if (environment.acceptsProfiles(Profiles.of(AppProfiles.saas))) {
      val title = "Important Note: feedless 1 is online!"
      val repositoryId = feedlessOpsNotificationRepo.id

      val notification = documentDAO.findByContentTitleAndRepositoryId(title, repositoryId) ?: run {
        val n = DocumentEntity()
        n.repositoryId = repositoryId
        n
      }

      notification.url = propertyService.appHost
      notification.contentTitle = title
      notification.status = ReleaseStatus.released
      notification.contentText =
        "Hi, I released a new version of feedless, that gives you a lot of new features."
      notification.updatedAt = Date()

      documentDAO.save(notification)
    }
  }
  private fun pushLegacyNotifications(root: UserEntity) {
    val legacyNotificationRepo = resolveLegacyNotificationsRepo(root)

    if (environment.acceptsProfiles(Profiles.of(AppProfiles.saas))) {
      val title = "Service Announcement: Limited support for RSS-Proxy URLs"
      val repositoryId = legacyNotificationRepo.id

      val notification = documentDAO.findByContentTitleAndRepositoryId(title, repositoryId) ?: run {
        val d = DocumentEntity()
        d.repositoryId = repositoryId
        d
      }

      notification.url = "https://feedless.org"
      notification.contentTitle = title
      notification.status = ReleaseStatus.released
      notification.contentText =
        "Dear user, support for anonymous rss-proxy feeds is coming to an end, new feeds can be created on https://feedless.org ."
      notification.contentHtml =
        "Dear user, support for anonymous rss-proxy feeds is coming to an end, new feeds can be created on <a href-\"https://feedless.org\">feedless.org</a>."
      notification.updatedAt = Date()

      documentDAO.save(notification)
    }
  }

  private fun resolveLegacyNotificationsRepo(root: UserEntity): RepositoryEntity {
    val repoTitleLegacyNotifications = legacyFeedService.getRepoTitleForLegacyFeedNotifications()
    return resolveOpsNotificationsRepo(repoTitleLegacyNotifications, root)
  }

  private fun resolveFeedlessOpsNotificationsRepo(root: UserEntity): RepositoryEntity {
    val repoTitleOpsNotifications = repositoryService.getRepoTitleForFeedlessOpsNotifications()
    return resolveOpsNotificationsRepo(repoTitleOpsNotifications, root)
  }

  private fun resolveOpsNotificationsRepo(repoTitle: String, root: UserEntity): RepositoryEntity {
    val repo = RepositoryEntity()
    repo.title = repoTitle
    repo.description = ""
    repo.shareKey = ""
    repo.ownerId = root.id
    repo.visibility = EntityVisibility.isPrivate
    repo.product = ProductCategory.feedless
    repo.sourcesSyncCron = ""

    return repositoryDAO.findByTitleAndOwnerId(repoTitle, root.id) ?: repositoryDAO.save(repo)
  }

  private fun createAnonymousUser() = createUser(
    propertyService.anonymousEmail,
    isAnonymous = true,
  )

  private fun createUser(
    email: String,
    isRoot: Boolean = false,
    isAnonymous: Boolean = false
  ): UserEntity {
    if (userDAO.existsByEmail(email)) {
      throw BadRequestException("user already exists")
    }
    log.info("create internal user $email")
    val user = UserEntity()
    user.email = email
    user.root = isRoot
    user.anonymous = isAnonymous
    user.hasAcceptedTerms = isRoot || isAnonymous
//    user.planId = planDAO.findByNameAndProduct(plan, ProductName.system)!!.id
    return userDAO.saveAndFlush(user)
  }


  private fun seedProducts(root: UserEntity) {
    val baseFeatureGroup =
      featureGroupDAO.findByParentFeatureGroupIdIsNull() ?: run {
        val group = FeatureGroupEntity()
        group.name = "server"
        featureGroupDAO.save(group)
      }

    featureService.assignFeatureValues(
      baseFeatureGroup, features = mapOf(
        FeatureName.requestPerMinuteUpperLimitInt to asIntFeature(40),
        FeatureName.refreshRateInMinutesLowerLimitInt to asIntFeature(120),
        FeatureName.publicRepositoryBool to asBoolFeature(false),
        FeatureName.pluginsBool to asBoolFeature(false),
        FeatureName.legacyApiBool to asBoolFeature(true),
        FeatureName.legacyAnonymousFeedSupportEolInt to asIntFeature(1718265907060),

        FeatureName.repositoryCapacityLowerLimitInt to asIntFeature(0),
        FeatureName.repositoryCapacityUpperLimitInt to asIntFeature(0),
        FeatureName.repositoryRetentionMaxDaysLowerLimitInt to asIntFeature(0),

        FeatureName.scrapeRequestTimeoutMsecInt to asIntFeature(0),
        FeatureName.scrapeSourceMaxCountTotalInt to asIntFeature(0),
        FeatureName.scrapeSourceMaxCountActiveInt to asIntFeature(0),
        FeatureName.scrapeRequestActionMaxCountInt to asIntFeature(0),
        FeatureName.sourceMaxCountPerRepositoryInt to asIntFeature(0),

        FeatureName.canJoinPlanWaitList to asBoolFeature(false),
        FeatureName.canActivatePlan to asBoolFeature(false),
        FeatureName.canLogin to asBoolFeature(true),
        FeatureName.canSignUp to asBoolFeature(true),
        FeatureName.canCreateUser to asBoolFeature(true),

        FeatureName.itemEmailForwardBool to asBoolFeature(false),
        FeatureName.itemWebhookForwardBool to asBoolFeature(false),
      )
    )

    if (isSelfHosted()) {
      featureService.assignFeatureValues(
        baseFeatureGroup, features = mapOf(
//          FeatureName.requestPerMinuteUpperLimitInt to asIntFeature(40),
//          FeatureName.refreshRateInMinutesLowerLimitInt to asIntFeature(120),
          FeatureName.publicRepositoryBool to asBoolFeature(true),
          FeatureName.pluginsBool to asBoolFeature(true),
          FeatureName.legacyAnonymousFeedSupportEolInt to asIntFeature(null),
//          FeatureName.legacyApiBool to asBoolFeature(true),

          FeatureName.repositoryCapacityLowerLimitInt to asIntFeature(2),
          FeatureName.repositoryCapacityUpperLimitInt to asIntFeature(10000),
          FeatureName.repositoryRetentionMaxDaysLowerLimitInt to asIntFeature(2),

          FeatureName.scrapeRequestTimeoutMsecInt to asIntFeature(30000),
          FeatureName.scrapeSourceMaxCountTotalInt to asIntFeature(10000),
          FeatureName.scrapeSourceMaxCountActiveInt to asIntFeature(10000),
          FeatureName.scrapeRequestActionMaxCountInt to asIntFeature(5),
          FeatureName.sourceMaxCountPerRepositoryInt to asIntFeature(2),

//          FeatureName.hasWaitList to asBoolFeature(false),
          FeatureName.canLogin to asBoolFeature(true),
          FeatureName.canSignUp to asBoolFeature(true),
          FeatureName.canCreateUser to asBoolFeature(true),

//          FeatureName.itemEmailForwardBool to asBoolFeature(false),
//          FeatureName.itemWebhookForwardBool to asBoolFeature(false),
        )
      )
    } else {
      val price = 59.99
      createProduct(
        "RSS-proxy", "Current version incl. all minor and patch",
        group = ProductCategory.rssProxy,
        isCloud = false,
        prices = listOf(
          createPricedProduct(
            individual = true,
            price = price,
            unit = "First major release"
          ),
          createPricedProduct(
            individual = true,
            price = floor(price * 0.5),
            unit = "Second consecutive releases"
          ),
          createPricedProduct(
            individual = true,
            price = floor(price * 0.4),
            unit = "Third consecutive releases onwards"
          ),
          createPricedProduct(
            other = true,
            unit = "Major Release",
            price = 4.99
          ),
        )
      )
      createProduct(
        "All Products Forever",
        "Everything released under the feedless umbrella, forever",
        isCloud = false,
        prices = listOf(
          createPricedProduct(
            individual = true,
            price = 399.99,
            inStock = 50,
            unit = "One time"
          )
        )
      )

      val rpFree = createProduct(
        "RSS-proxy Free",
        "Getting started",
        group = ProductCategory.rssProxy,
        isBaseProduct = true,
        isCloud = true,
        prices = listOf(
          createPricedProduct(
            individual = true,
            enterprise = true,
            other = true,
            unit = "Per Month",
            price = 0.0
          ),
        ),
        parentFeatureGroup = baseFeatureGroup,
        features = mapOf(
//          FeatureName.requestPerMinuteUpperLimitInt to asIntFeature(40),
//          FeatureName.refreshRateInMinutesLowerLimitInt to asIntFeature(120),
//          FeatureName.publicRepositoryBool to asBoolFeature(false),
          FeatureName.pluginsBool to asBoolFeature(true),

          FeatureName.repositoryCapacityLowerLimitInt to asIntFeature(2),
          FeatureName.repositoryCapacityUpperLimitInt to asIntFeature(10),
          FeatureName.repositoryRetentionMaxDaysLowerLimitInt to asIntFeature(7),

          FeatureName.scrapeRequestTimeoutMsecInt to asIntFeature(30000),
          FeatureName.scrapeSourceMaxCountTotalInt to asIntFeature(10),
          FeatureName.scrapeSourceMaxCountActiveInt to asIntFeature(10),
          FeatureName.scrapeRequestActionMaxCountInt to asIntFeature(10), // todo check
          FeatureName.sourceMaxCountPerRepositoryInt to asIntFeature(5),

//          FeatureName.hasWaitList to asBoolFeature(false),
          FeatureName.canActivatePlan to asBoolFeature(true),

//          FeatureName.itemEmailForwardBool to asBoolFeature(false),
//          FeatureName.itemWebhookForwardBool to asBoolFeature(false),
        )
      )

      createProduct(
        "RSS-proxy Pro",
        "Getting serious",
        group = ProductCategory.rssProxy,
        isCloud = true,
        parentFeatureGroup = rpFree.featureGroup!!,
        prices = listOf(
          createPricedProduct(
            individual = true,
            enterprise = true,
            other = true,
            unit = "Per Month",
            price = -1.0
          ),
        ),
        features = mapOf(
//          FeatureName.requestPerMinuteUpperLimitInt to asIntFeature(40),
          FeatureName.refreshRateInMinutesLowerLimitInt to asIntFeature(10),
          FeatureName.publicRepositoryBool to asBoolFeature(true),
          FeatureName.pluginsBool to asBoolFeature(true),

//          FeatureName.repositoryCapacityLowerLimitInt to asIntFeature(2),
          FeatureName.repositoryCapacityUpperLimitInt to asIntFeature(10000),
          FeatureName.repositoryRetentionMaxDaysLowerLimitInt to asIntFeature(2),

          FeatureName.scrapeRequestTimeoutMsecInt to asIntFeature(60000),
          FeatureName.scrapeSourceMaxCountTotalInt to asIntFeature(null),
          FeatureName.scrapeSourceMaxCountActiveInt to asIntFeature(null),
          FeatureName.scrapeRequestActionMaxCountInt to asIntFeature(20),
          FeatureName.sourceMaxCountPerRepositoryInt to asIntFeature(10),

          FeatureName.canJoinPlanWaitList to asBoolFeature(true),
          FeatureName.canActivatePlan to asBoolFeature(false),

          FeatureName.itemEmailForwardBool to asBoolFeature(true),
          FeatureName.itemWebhookForwardBool to asBoolFeature(true),
        )
      )

      val feedlessFree = createProduct(
        "feedless Free",
        "Getting started",
        group = ProductCategory.feedless,
        isBaseProduct = true,
        isCloud = true,
        prices = listOf(
          createPricedProduct(
            individual = true,
            enterprise = true,
            other = true,
            unit = "Per Month",
            price = 0.0
          ),
        ),
        parentFeatureGroup = baseFeatureGroup,
        features = mapOf(
//          FeatureName.requestPerMinuteUpperLimitInt to asIntFeature(40),
//          FeatureName.refreshRateInMinutesLowerLimitInt to asIntFeature(120),
//          FeatureName.publicRepositoryBool to asBoolFeature(false),
          FeatureName.pluginsBool to asBoolFeature(true),

          FeatureName.repositoryCapacityLowerLimitInt to asIntFeature(2),
          FeatureName.repositoryCapacityUpperLimitInt to asIntFeature(1000),
          FeatureName.repositoryRetentionMaxDaysLowerLimitInt to asIntFeature(7),

          FeatureName.scrapeRequestTimeoutMsecInt to asIntFeature(30000),
          FeatureName.scrapeSourceMaxCountTotalInt to asIntFeature(10),
          FeatureName.scrapeSourceMaxCountActiveInt to asIntFeature(10),
          FeatureName.scrapeRequestActionMaxCountInt to asIntFeature(10), // todo check
          FeatureName.sourceMaxCountPerRepositoryInt to asIntFeature(5),

//          FeatureName.hasWaitList to asBoolFeature(false),
          FeatureName.canActivatePlan to asBoolFeature(true),

//          FeatureName.itemEmailForwardBool to asBoolFeature(false),
//          FeatureName.itemWebhookForwardBool to asBoolFeature(false),
        )
      )

    }
  }

  private fun resolveFeatureGroup(
    name: String,
    parentFeatureGroup: FeatureGroupEntity?,
    features: Map<FeatureName, FeatureValueEntity>
  ): FeatureGroupEntity {
    val group = featureGroupDAO.findByName(name) ?: run {
      val group = FeatureGroupEntity()
      group.name = name
      group.parentFeatureGroupId = parentFeatureGroup?.id
      featureGroupDAO.save(group)
    }

    featureService.assignFeatureValues(group, features)
    return group
  }

  private fun createPricedProduct(
    individual: Boolean = false,
    enterprise: Boolean = false,
    other: Boolean = false,
    inStock: Int? = null,
    unit: String,
    price: Double
  ): PricedProductEntity {
    val priced = PricedProductEntity()
    priced.individual = individual
    priced.other = other
    priced.enterprise = enterprise
    priced.inStock = inStock
    priced.price = price
    priced.unit = unit

    return priced
  }

  private fun createProduct(
    name: String,
    description: String,
    group: ProductCategory? = null,
    prices: List<PricedProductEntity>,
    parentFeatureGroup: FeatureGroupEntity? = null,
    features: Map<FeatureName, FeatureValueEntity>? = null,
    isCloud: Boolean = false,
    isBaseProduct: Boolean = false
  ): ProductEntity {

    val product = productDAO.findByName(name) ?: run {
      val product = ProductEntity()
      product.name = name
      product.description = description
      product.baseProduct = isBaseProduct
      product.partOf = group

      productDAO.save(product)
    }

    features?.let {
      val featureGroup = resolveFeatureGroup(name, parentFeatureGroup, features)
      product.featureGroupId = featureGroup.id
      product.featureGroup = featureGroup
      product.isCloudProduct = isCloud
      productDAO.save(product)
    }

    pricedProductDAO.deleteAllByProductId(product.id)
    pricedProductDAO.saveAll(prices.map {
      it.productId = product.id
      it
    })

    return product
  }

  private fun isSelfHosted() = environment.acceptsProfiles(Profiles.of(AppProfiles.selfHosted))

  //

  private fun asIntFeature(value: Long?): FeatureValueEntity {
    val feature = FeatureValueEntity()
    feature.valueType = FeatureValueType.number
    feature.valueInt = value
    return feature
  }

  private fun asBoolFeature(value: Boolean): FeatureValueEntity {
    val feature = FeatureValueEntity()
    feature.valueType = FeatureValueType.bool
    feature.valueBoolean = value
    return feature
  }

}
