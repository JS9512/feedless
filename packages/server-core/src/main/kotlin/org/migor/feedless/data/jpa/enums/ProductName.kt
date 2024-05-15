package org.migor.feedless.data.jpa.enums

enum class ProductName {
  visualDiff,
  pageChangeTracker,
  rssProxy,
  reader,
  upcoming,
  digest,
  feedless,
  feedDump,
  untoldNotes,
  system
}

fun org.migor.feedless.generated.types.ProductName.fromDto(): ProductName {
  return when (this) {
    org.migor.feedless.generated.types.ProductName.visualDiff -> ProductName.visualDiff
//    org.migor.feedless.generated.types.ProductName.pageChangeTracker -> ProductName.pageChangeTracker
    org.migor.feedless.generated.types.ProductName.rssProxy -> ProductName.rssProxy
    org.migor.feedless.generated.types.ProductName.reader -> ProductName.reader
//    org.migor.feedless.generated.types.ProductName.upcoming -> ProductName.upcoming
//    org.migor.feedless.generated.types.ProductName.digest -> ProductName.digest
    org.migor.feedless.generated.types.ProductName.feedless -> ProductName.feedless
    org.migor.feedless.generated.types.ProductName.feedDump -> ProductName.feedDump
    org.migor.feedless.generated.types.ProductName.untoldNotes -> ProductName.untoldNotes
    else -> throw IllegalArgumentException("$this is not a valid product name")
  }
}
