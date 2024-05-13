package org.migor.feedless.jobs

import org.migor.feedless.AppProfiles
import org.migor.feedless.secrets.OneTimePasswordDAO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Profile(AppProfiles.database)
@Transactional(propagation = Propagation.NEVER)
class CleanupJob internal constructor() {

  private val log = LoggerFactory.getLogger(CleanupJob::class.simpleName)

  @Autowired
  lateinit var oneTimePasswordDAO: OneTimePasswordDAO

  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  fun executeCleanup() {
    oneTimePasswordDAO.deleteAllByValidUntilBefore(Date())
  }
}