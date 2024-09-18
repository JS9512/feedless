package org.migor.feedless.user

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity

@Entity
@DiscriminatorValue("telegram")
open class TelegramConnectionEntity : ConnectedAppEntity() {
  @Column(name = "chat_id")
  open var chatId: Long = 0
}
