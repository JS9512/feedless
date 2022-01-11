package org.migor.rich.rss.api

import org.migor.rich.rss.user.SignupUserDto
import org.migor.rich.rss.user.UserService
import org.migor.rich.rss.util.CryptUtil.handleCorrId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import java.net.URI

@Controller
class UserController {

  private val log = LoggerFactory.getLogger(UserController::class.simpleName)

  @Autowired
  lateinit var userService: UserService

  @PutMapping("/api/users")
  fun signup(
    @RequestBody signupUser: SignupUserDto,
    @RequestParam("correlationId", required = false) correlationId: String?
  ): ResponseEntity<String> {
    try {
      val user = userService.signup(handleCorrId(correlationId), signupUser)

      return ResponseEntity.created(URI("http://l/wefwef"))
        .header("Content-Type", "application/json")
        .body(user.toJson())

    } catch (e: Exception) {
      log.error("", e)
    }
    return ResponseEntity.badRequest().build()
  }

}