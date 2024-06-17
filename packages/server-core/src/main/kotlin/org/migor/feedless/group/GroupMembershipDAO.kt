package org.migor.feedless.group

import org.migor.feedless.AppProfiles
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile(AppProfiles.database)
interface GroupMembershipDAO : JpaRepository<GroupMembershipEntity, UUID> {
  fun existsByUserIdAndGroupId(userId: UUID, groupId: UUID): Boolean
  fun findAllByUserId(userId: UUID?): List<GroupMembershipEntity>
}
