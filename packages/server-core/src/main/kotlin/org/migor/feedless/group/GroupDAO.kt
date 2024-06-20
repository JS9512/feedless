package org.migor.feedless.group

import org.migor.feedless.AppProfiles
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile(AppProfiles.database)
interface GroupDAO : JpaRepository<GroupEntity, UUID> {
  fun findByName(name: String): GroupEntity?
}
