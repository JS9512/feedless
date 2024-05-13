package org.migor.feedless.data.jpa.repositories

import org.migor.feedless.AppProfiles
import org.migor.feedless.data.jpa.models.SourceEntity
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile(AppProfiles.database)
interface SourceDAO : JpaRepository<SourceEntity, UUID> {
  fun findAllByRepositoryId(id: UUID): List<SourceEntity>

  @Modifying
  @Query(
      """
      update SourceEntity C
        set C.erroneous = :erroneous,
            C.lastErrorMessage = :errorMessage
      where C.id = :id
    """
  )
  fun setErrornous(
    @Param("id") id: UUID,
    @Param("erroneous") erroneous: Boolean,
    @Param("errorMessage") errorMessage: String? = null
  )

}