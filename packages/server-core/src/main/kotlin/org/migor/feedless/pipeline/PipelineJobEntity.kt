package org.migor.feedless.pipeline

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.Type
import org.migor.feedless.data.jpa.EntityWithUUID
import org.migor.feedless.data.jpa.models.WebDocumentEntity
import org.migor.feedless.generated.types.PluginExecutionParamsInput
import java.util.*

@Entity
@Table(name = "t_pipeline_job")
open class PipelineJobEntity : EntityWithUUID() {

  @Column(nullable = false, name = "sequence_id")
  open var sequenceId: Int = -1

  @Column(nullable = false)
  open var attempt: Int = 0

  @Column(nullable = false)
  open lateinit var executorId: String

  @Type(JsonBinaryType::class)
  @Column(columnDefinition = "jsonb")
  open lateinit var executorParams: PluginExecutionParamsInput

  open var terminatedAt: Date? = null

  @Column(name = "cool_down_until")
  open var coolDownUntil: Date? = null

  open var terminated: Boolean = false

//  @Basic
//  open var terminatedAt: Timestamp? = null
//
//  @Basic
//  @Column(columnDefinition = "TEXT")
//  open var logs: String? = null

  @Column(name = "webdocument_id", nullable = false)
  open lateinit var webDocumentId: UUID

  @ManyToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(
    name = "webdocument_id",
    referencedColumnName = "id",
    insertable = false,
    updatable = false,
    foreignKey = ForeignKey(name = "fk_attachment__web_document")
  )
  open var webDocument: WebDocumentEntity? = null

  @PrePersist
  fun prePersist() {
    if (terminated) {
      terminatedAt = Date()
    }
  }
}
