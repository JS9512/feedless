package org.migor.feedless.user

import org.migor.feedless.AppLayer
import org.migor.feedless.AppProfiles
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("${AppProfiles.user} & ${AppLayer.repository}")
interface ConnectedAppDAO : JpaRepository<ConnectedAppEntity, UUID> {
  fun findByIdAndAuthorizedFalse(id: UUID): ConnectedAppEntity?
  fun findAllByUserId(userId: UUID): List<ConnectedAppEntity>
}
