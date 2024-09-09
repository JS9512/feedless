package org.migor.feedless.repository

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.migor.feedless.AppProfiles
import org.migor.feedless.api.ApiParams
import org.migor.feedless.api.throttle.Throttled
import org.migor.feedless.common.PropertyService
import org.migor.feedless.data.jpa.enums.fromDto
import org.migor.feedless.generated.DgsConstants
import org.migor.feedless.generated.types.CountRepositoriesInput
import org.migor.feedless.generated.types.CronRun
import org.migor.feedless.generated.types.RepositoriesCreateInput
import org.migor.feedless.generated.types.RepositoriesInput
import org.migor.feedless.generated.types.Repository
import org.migor.feedless.generated.types.RepositoryUniqueWhereInput
import org.migor.feedless.generated.types.RepositoryUpdateInput
import org.migor.feedless.generated.types.RepositoryWhereInput
import org.migor.feedless.generated.types.ScrapeRequest
import org.migor.feedless.pipeline.PipelineJobStatus
import org.migor.feedless.pipeline.SourcePipelineJobDAO
import org.migor.feedless.pipeline.SourcePipelineJobEntity
import org.migor.feedless.session.SessionService
import org.migor.feedless.session.useRequestContext
import org.migor.feedless.source.SourceDAO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestHeader
import java.util.*

@DgsComponent
@Profile("${AppProfiles.database} & ${AppProfiles.api}")
@Transactional
class RepositoryResolver {

  private val log = LoggerFactory.getLogger(RepositoryResolver::class.simpleName)

  @Autowired
  private lateinit var repositoryService: RepositoryService

  @Autowired
  private lateinit var sessionService: SessionService

  @Autowired
  private lateinit var sourceDAO: SourceDAO

  @Autowired
  private lateinit var sourcePipelineJobDAO: SourcePipelineJobDAO


  @Throttled
  @DgsQuery
  suspend fun repositories(
    @InputArgument data: RepositoriesInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<Repository> = withContext(useRequestContext(currentCoroutineContext())) {
    log.debug("[$corrId] repositories $data")
    val pageNumber = handlePageNumber(data.cursor.page)
    val pageSize = handlePageSize(data.cursor.pageSize)
    val offset = pageNumber * pageSize
    val userId = sessionService.userId()
    repositoryService.findAll(offset, pageSize, data.where, userId)
      .map { it.toDto() }
  }

  @Throttled
  @DgsQuery
  suspend fun countRepositories(
    @RequestHeader(ApiParams.corrId) corrId: String,
    @InputArgument data: CountRepositoriesInput,
  ): Int = withContext(useRequestContext(currentCoroutineContext())) {
    log.debug("[$corrId] countRepositories")
    repositoryService.countAll(sessionService.userId(), data.product.fromDto())
  }

  @Throttled
  @DgsQuery
  suspend fun repository(
    @InputArgument data: RepositoryWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): Repository = withContext(useRequestContext(currentCoroutineContext())) {
    log.debug("[$corrId] repository $data")
    repositoryService.findById(corrId, UUID.fromString(data.where.id)).toDto()
  }

  @Throttled
  @DgsMutation(field = DgsConstants.MUTATION.CreateRepositories)
  @PreAuthorize("hasAuthority('ANONYMOUS')")
  suspend fun createRepositories(
    @InputArgument("data") data: RepositoriesCreateInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<Repository> = withContext(useRequestContext(currentCoroutineContext())) {
    log.debug("[$corrId] createRepositories $data")
    repositoryService.create(corrId, data)
  }

  @Throttled
  @DgsMutation(field = DgsConstants.MUTATION.UpdateRepository)
  @PreAuthorize("hasAuthority('USER')")
  suspend fun updateRepository(
    @InputArgument("data") data: RepositoryUpdateInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): Repository = withContext(useRequestContext(currentCoroutineContext())) {
    log.debug("[$corrId] updateRepository $data")
    repositoryService.update(corrId, UUID.fromString(data.where.id), data.data).toDto()
  }

  @Throttled
  @DgsMutation(field = DgsConstants.MUTATION.DeleteRepository)
  @PreAuthorize("hasAuthority('USER')")
  suspend fun deleteRepository(
    @InputArgument("data") data: RepositoryUniqueWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): Boolean = withContext(useRequestContext(currentCoroutineContext())) {
    log.debug("[$corrId] deleteRepository $data")
    repositoryService.delete(corrId, UUID.fromString(data.id))
    true
  }


  @DgsData(parentType = DgsConstants.REPOSITORY.TYPE_NAME, field = DgsConstants.REPOSITORY.Sources)
  suspend fun sources(
    dfe: DgsDataFetchingEnvironment,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<ScrapeRequest> = coroutineScope {
    val repository: Repository = dfe.getSource()
    val sources = withContext(Dispatchers.IO) {
      sourceDAO.findAllByRepositoryIdOrderByCreatedAtDesc(UUID.fromString(repository.id)).map { it.toScrapeRequest(corrId) }
    }
    sources
  }

  @DgsData(parentType = DgsConstants.REPOSITORY.TYPE_NAME, field = DgsConstants.REPOSITORY.CronRuns)
  suspend fun cronRuns(
    dfe: DgsDataFetchingEnvironment,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): List<CronRun> = withContext(Dispatchers.IO) {
    val source: Repository = dfe.getSource()
    sourcePipelineJobDAO.findAllByRepositoryId(UUID.fromString(source.id)).map { it.toDto() }
  }

  @DgsData(parentType = DgsConstants.REPOSITORY.TYPE_NAME)
  suspend fun tags(dfe: DgsDataFetchingEnvironment): List<String> = withContext(Dispatchers.IO) {
    val source: Repository = dfe.getSource()
    sourceDAO.findAllByRepositoryIdOrderByCreatedAtDesc(UUID.fromString(source.id))
      .mapNotNull { it.tags?.asList() }
      .flatten()
      .distinct()
  }

}

private fun SourcePipelineJobEntity.toDto(): CronRun {
  return CronRun(
    isSuccessful = this.status === PipelineJobStatus.SUCCEEDED,
    message = StringUtils.trimToEmpty(this.logs),
    executedAt = this.terminatedAt!!.time
  )
}

private fun handlePageNumber(page: Int?): Int =
  page ?: 0

private fun handlePageSize(pageSize: Int?): Int =
  (pageSize ?: PropertyService.maxPageSize).coerceAtLeast(1).coerceAtMost(PropertyService.maxPageSize)
