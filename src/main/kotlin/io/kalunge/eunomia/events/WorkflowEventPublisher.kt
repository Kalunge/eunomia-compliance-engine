package io.kalunge.eunomia.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.kalunge.eunomia.domain.WorkflowEvent
import io.kalunge.eunomia.domain.WorkflowEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Persists workflow events to the audit log and dispatches them to
 * live SSE subscribers.
 *
 * Sequence numbers are assigned per-regulation by reading the current
 * max and adding one. The saga is single-threaded per regulation
 * (each workflow handles one regulation at a time on its own @Async
 * task), so the race window is narrow; the UNIQUE constraint on
 * (regulation_id, sequence_no) is the last line of defence.
 *
 * publish() is fail-soft — a failure to persist or dispatch should
 * never poison the saga itself. Callers wrap the call in runCatching
 * (or call publishQuietly which does it for them).
 */
@Component
class WorkflowEventPublisher(
    private val eventRepository: WorkflowEventRepository,
    private val sseFanout: SseFanout,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun publish(
        regulationId: UUID,
        tenantId: UUID,
        eventType: String,
        payload: Map<String, Any?> = emptyMap(),
    ): WorkflowEvent {
        val seq = eventRepository.maxSequence(regulationId) + 1
        val payloadJson = objectMapper.writeValueAsString(payload)
        val event = WorkflowEvent(
            tenantId = tenantId,
            regulationId = regulationId,
            sequenceNo = seq,
            eventType = eventType,
            payload = payloadJson,
        )
        val saved = eventRepository.save(event)
        sseFanout.dispatch(saved)
        log.debug("Published event {} seq={} for regulation {}", eventType, seq, regulationId)
        return saved
    }

    /**
     * Same as publish but swallows + logs any exception. Use this from
     * inside hot paths where event publishing failure must not break
     * the saga.
     */
    fun publishQuietly(
        regulationId: UUID,
        tenantId: UUID,
        eventType: String,
        payload: Map<String, Any?> = emptyMap(),
    ) {
        runCatching { publish(regulationId, tenantId, eventType, payload) }
            .onFailure { log.warn("Event publish failed (non-fatal) for {}: {}", eventType, it.message) }
    }
}
