package org.migor.feedless.api.auth

import jakarta.servlet.http.HttpServletResponse
import org.migor.feedless.AppProfiles
import org.migor.feedless.data.jpa.enums.AuthSource
import org.migor.feedless.data.jpa.enums.fromDto
import org.migor.feedless.data.jpa.models.FeatureName
import org.migor.feedless.data.jpa.models.OneTimePasswordEntity
import org.migor.feedless.data.jpa.models.PlanName
import org.migor.feedless.data.jpa.models.UserEntity
import org.migor.feedless.data.jpa.repositories.OneTimePasswordDAO
import org.migor.feedless.data.jpa.repositories.UserDAO
import org.migor.feedless.generated.types.AuthViaMailInput
import org.migor.feedless.generated.types.AuthenticationEvent
import org.migor.feedless.generated.types.ConfirmAuthCodeInput
import org.migor.feedless.generated.types.ConfirmCode
import org.migor.feedless.generated.types.ProductName
import org.migor.feedless.service.FeatureService
import org.migor.feedless.service.OneTimePasswordService
import org.migor.feedless.service.PropertyService
import org.migor.feedless.service.UserService
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Service
@Profile(AppProfiles.database)
class MailAuthenticationService {
  private val log = LoggerFactory.getLogger(MailAuthenticationService::class.simpleName)

  @Autowired
  lateinit var propertyService: PropertyService

  @Autowired
  lateinit var tokenProvider: TokenProvider

  @Autowired
  lateinit var cookieProvider: CookieProvider

  @Autowired
  lateinit var oneTimePasswordDAO: OneTimePasswordDAO

  @Autowired
  lateinit var userService: UserService

  @Autowired
  lateinit var featureService: FeatureService

  @Autowired
  lateinit var userDAO: UserDAO

  @Autowired
  lateinit var oneTimePasswordService: OneTimePasswordService

  fun authenticateUsingMail(corrId: String, data: AuthViaMailInput): Publisher<AuthenticationEvent> {
    val email = data.email
    log.info("[${corrId}] init user session for $email")
    return Flux.create { emitter -> run {
      try {
        if (featureService.isDisabled(FeatureName.canLogin, data.product.fromDto())) {
          throw IllegalArgumentException("login is deactivated")
        }

        val user = resolveUserByMail(corrId, data)

        val otp = if (user == null) {
          oneTimePasswordService.createOTP()
        } else {
          oneTimePasswordService.createOTP(corrId, user, data.osInfo)
        }

        Mono.delay(Duration.ofSeconds(2)).subscribe {
          emitter.next(
            AuthenticationEvent.newBuilder()
              .confirmCode(
                ConfirmCode.newBuilder()
                  .length(otp.password.length)
                  .otpId(otp.id.toString())
                  .build()
              )
              .build()
          )
          emitter.complete()
        }
        emitter.onDispose { log.debug("[$corrId] disconnected") }

      } catch (e: Exception) {
        log.error("[$corrId] ${e.message}")
        emitter.error(e)
      }
    }
    }
  }

  private fun resolveUserByMail(corrId: String, data: AuthViaMailInput): UserEntity? {
    return userDAO.findByEmail(data.email) ?: if (data.allowCreate) { createUser(corrId, data.email, product=data.product) } else {null}
  }

  private fun createUser(corrId: String, email: String, product: ProductName): UserEntity {
    return userService.createUser(corrId, email, product.fromDto(), AuthSource.email, PlanName.minimal)
  }

  //  @Transactional
  fun confirmAuthCode(corrId: String, codeInput: ConfirmAuthCodeInput, response: HttpServletResponse) {
    val otpId = UUID.fromString(codeInput.otpId)
    val otp = oneTimePasswordDAO.findById(otpId)

    otp.ifPresentOrElse({
      if (isOtpExpired(it)) {
        throw RuntimeException("code expired. Please restart authentication ($corrId)")
      }
      if (it.password != codeInput.code) {
        throw RuntimeException("invalid code ($corrId)")
      }

      oneTimePasswordDAO.deleteById(otpId)

      val jwt = tokenProvider.createJwtForUser(it.user!!)

      response.addCookie(cookieProvider.createTokenCookie(corrId, jwt))
    },
      {
        log.error("otp not found ($corrId)")
      })
  }

  private fun isOtpExpired(otp: OneTimePasswordEntity) =
    otp.validUntil.before(Timestamp.valueOf(LocalDateTime.now()))

}
