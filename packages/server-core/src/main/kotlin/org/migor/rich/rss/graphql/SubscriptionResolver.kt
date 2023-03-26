package org.migor.rich.rss.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment
import org.migor.rich.rss.generated.types.AuthenticationEvent
import org.migor.rich.rss.service.MailAuthenticationService
import org.migor.rich.rss.util.CryptUtil.newCorrId
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired


@DgsComponent
class SubscriptionResolver {

  private val log = LoggerFactory.getLogger(SubscriptionResolver::class.simpleName)

  @Autowired
  lateinit var mailAuthenticationService: MailAuthenticationService

  @DgsSubscription
  fun authViaMail(
    @InputArgument email: String,
    dfe: DataFetchingEnvironment,
  ): Publisher<AuthenticationEvent> {
    log.info("${DgsContext.from(dfe).requestData}")
    return mailAuthenticationService.initiateMailAuthentication(newCorrId(), email)
  }
}