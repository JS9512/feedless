package org.migor.feedless.group

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import kotlinx.coroutines.coroutineScope
import org.migor.feedless.AppProfiles
import org.migor.feedless.generated.DgsConstants
import org.migor.feedless.generated.types.Group
import org.migor.feedless.generated.types.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@DgsComponent
@Profile("${AppProfiles.database} & ${AppProfiles.api}")
class GroupResolver {

  @Autowired
  private lateinit var groupMembershipDAO: GroupMembershipDAO

  @DgsData(parentType = DgsConstants.USER.TYPE_NAME)
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun groups(dfe: DgsDataFetchingEnvironment): List<Group> = coroutineScope {
    val user: User = dfe.getSource()
    groupMembershipDAO.findAllByUserId(UUID.fromString(user.id))
      .map {
        Group.newBuilder()
          .id(it.groupId.toString())
          .build()
      }
  }
}
