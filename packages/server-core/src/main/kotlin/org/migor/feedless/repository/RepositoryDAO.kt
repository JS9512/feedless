package org.migor.feedless.repository

import org.migor.feedless.AppProfiles
import org.migor.feedless.data.jpa.enums.EntityVisibility
import org.migor.feedless.data.jpa.enums.ProductCategory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*
import java.util.stream.Stream

@Repository
@Profile(AppProfiles.database)
interface RepositoryDAO : JpaRepository<RepositoryEntity, UUID> {

  @Query(
    """
      select distinct e from RepositoryEntity e
      inner join UserEntity u
        on u.id = e.ownerId
      where e.archived = false
        and (e.triggerScheduledNextAt is null or e.triggerScheduledNextAt < :now)
        and u.locked = false
        and e.sourcesSyncExpression > ''
        and (e.disabledFrom is null or e.disabledFrom > :now)
      order by e.lastUpdatedAt asc """,
  )
  fun findSomeDue(@Param("now") now: Date, pageable: Pageable): Stream<RepositoryEntity>

  @Modifying
  @Query(
    """
    update RepositoryEntity e
    set e.triggerScheduledNextAt = :scheduledNextAt
    where e.id = :id
    """
  )
  fun updateScheduledNextAt(@Param("id") id: UUID, @Param("scheduledNextAt") scheduledNextAt: Date)

  @Modifying
  @Query(
    """
    update RepositoryEntity e
    set e.lastUpdatedAt = :lastUpdatedAt
    where e.id = :id
    """
  )
  fun updateLastUpdatedAt(@Param("id") id: UUID, @Param("lastUpdatedAt") lastUpdatedAt: Date)

  @Query(
    """
    SELECT r FROM RepositoryEntity r
    WHERE r.ownerId = :ownerId OR r.groupId = (
        SELECT g.id FROM GroupEntity g
        INNER JOIN GroupMembershipEntity m ON g.id = m.groupId
        WHERE m.userId = :ownerId
    )
    """
  )
  fun findAllByOwnerId(@Param("ownerId") ownerId: UUID, pageable: PageRequest): List<RepositoryEntity>

  fun countByOwnerId(id: UUID): Int
  fun countByOwnerIdAndArchived(id: UUID, archived: Boolean): Int
  fun countAllByOwnerIdAndProduct(it: UUID, product: ProductCategory): Int
  fun countAllByVisibility(visibility: EntityVisibility): Int
  fun findAllByVisibility(visibility: EntityVisibility, pageable: PageRequest): List<RepositoryEntity>
  fun existsByTitleAndOwnerId(title: String, id: UUID): Boolean

  fun findByTitleAndOwnerId(title: String, ownerId: UUID): RepositoryEntity

  @Query(
    """
    SELECT case when count(r)> 0 then true else false end FROM RepositoryEntity r
    WHERE r.ownerId = :ownerId OR r.groupId = (
        SELECT g.id FROM GroupEntity g
        INNER JOIN GroupMembershipEntity m ON g.id = m.groupId
        WHERE m.userId = :ownerId
    ) AND r.id = :repositoryId
    """
  )
  fun hasViewerPermissions(@Param("ownerId") userId: UUID, @Param("repositoryId") repositoryId: UUID): Boolean

//  @Modifying
//  @Query(
//    """
//    update SourceSubscriptionEntity e
//    set e.archived = true
//    where e.id IN (
//        select s.id from SourceSubscriptionEntity s
//        where s.ownerId = :ownerId
//          and s.archived = false
//        order by s.createdAt desc
//        limit 1
//    )
//    """
//  )
//  fun updateArchivedForOldestActive(@Param("ownerId") ownerId: UUID)

}
