package org.migor.feedless.group

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.migor.feedless.data.jpa.EntityWithUUID
import org.migor.feedless.data.jpa.StandardJpaFields
import org.migor.feedless.user.UserEntity
import java.util.*

@Entity
@Table(
  name = "t_group_membership",
  uniqueConstraints = [
    UniqueConstraint(name = "UniqueGroupMembership", columnNames = [StandardJpaFields.userId, StandardJpaFields.groupId])]
)
open class GroupMembershipEntity : EntityWithUUID() {

  @Column(name = StandardJpaFields.userId, nullable = false)
  open var userId: UUID? = null

  @OneToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(
    name = StandardJpaFields.userId,
    referencedColumnName = "id",
    insertable = false,
    updatable = false,
    foreignKey = ForeignKey(name = "fk_group_membership__to__user")
  )
  open var user: UserEntity? = null

  @Column(name = StandardJpaFields.groupId, nullable = false)
  open var groupId: UUID? = null

  @OneToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(
    name = StandardJpaFields.groupId,
    referencedColumnName = "id",
    insertable = false,
    updatable = false,
    foreignKey = ForeignKey(name = "fk_group_membership__to__group")
  )
  open var group: GroupEntity? = null
}
