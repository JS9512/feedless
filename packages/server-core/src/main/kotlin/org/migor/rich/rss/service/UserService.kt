package org.migor.rich.rss.service

import org.migor.rich.rss.AppProfiles
import org.migor.rich.rss.api.ApiErrorCode
import org.migor.rich.rss.api.ApiException
import org.migor.rich.rss.auth.JwtParameterNames
import org.migor.rich.rss.data.jpa.models.StreamEntity
import org.migor.rich.rss.data.jpa.models.UserEntity
import org.migor.rich.rss.data.jpa.repositories.StreamDAO
import org.migor.rich.rss.data.jpa.repositories.UserDAO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.util.*

@Service
@Profile(AppProfiles.database)
class UserService {

  private val log = LoggerFactory.getLogger(UserService::class.simpleName)

  @Autowired
  lateinit var userDAO: UserDAO

  @Autowired
  lateinit var streamDAO: StreamDAO

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun createUser(name: String, email: String, isRoot: Boolean = false): UserEntity {
    if (userDAO.existsByEmail(email)) {
      throw ApiException(ApiErrorCode.INTERNAL_ERROR, "user already exists")
    }
    log.info("create user $email")
    val user = UserEntity()
    user.name = name
    user.email = email
    user.isRoot = isRoot
    user.notificationsStream = streamDAO.saveAndFlush(StreamEntity())
    return userDAO.saveAndFlush(user)
  }

  fun findById(id: UUID): Optional<UserEntity> {
    return userDAO.findById(id)
  }

  fun findByEmail(email: String): Optional<UserEntity> {
    return userDAO.findByEmail(email)
  }

  fun getSystemUser(): UserEntity {
    return this.userDAO.findByName("root").orElseThrow()
  }

  fun acceptTermsAndConditions() {
    val id = (SecurityContextHolder.getContext().authentication as OAuth2AuthenticationToken).principal.attributes[JwtParameterNames.USER_ID] as String
    val user = userDAO.findById(UUID.fromString(id)).orElseThrow()
    user.hasApprovedTerms = true
    user.approvedTermsAt = Timestamp.from(Date().toInstant())
    userDAO.saveAndFlush(user)
  }

}
