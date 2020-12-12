package org.migor.rss.rich.repository

import org.migor.rss.rich.model.Entry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EntryRepository: PagingAndSortingRepository<Entry, String> {
  fun findAllBySubscriptionId(subscriptionId: String, pageable: Pageable): Page<Entry>
  fun findByLink(link: String?): Optional<Entry>
}
