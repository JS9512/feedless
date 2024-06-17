package org.migor.feedless.group

import org.migor.feedless.AppProfiles
import org.migor.feedless.user.UserEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile(AppProfiles.database)
class GroupService {

  private val log = LoggerFactory.getLogger(GroupService::class.simpleName)

  @Autowired
  private lateinit var groupMembershipDAO: GroupMembershipDAO

  fun getAdminGroupName(): String = "admin"
  fun addUserToGroup(user: UserEntity, group: GroupEntity): GroupMembershipEntity {
    val membership = GroupMembershipEntity()
    membership.userId = user.id
    membership.groupId = group.id

    return groupMembershipDAO.save(membership)
  }
}
