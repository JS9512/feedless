package org.migor.rss.rich.database.model

import org.hibernate.annotations.GenericGenerator
import org.migor.rss.rich.database.enums.ExporterTargetType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "\"ArticleExporterTarget\"")
class ExporterTarget: JsonSupport() {

  @Id
  @GeneratedValue(generator = "uuid")
  @GenericGenerator(name = "uuid", strategy = "uuid2")
  var id: String? = null

  @Column(name = "type")
  @Enumerated(EnumType.STRING)
  var type: ExporterTargetType? = null

  @Column(name = "forward_errors")
  var forwardErrors: Boolean = false

//  @Column(name = "context")
//  @Type(type = "jsonb")
//  @Basic(fetch = FetchType.LAZY)
//  var context: Map<String, Any>? = null

  @Column(name = "\"exporterId\"")
  var exporterId: String? = null
}
