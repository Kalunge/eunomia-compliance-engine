package io.kalunge.eunomia.events

import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.WorkflowEventRepository
import io.kalunge.eunomia.regulation.RegulationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.UUID

/**
 * Subscribes an [SseEmitter] to the live event stream for one
 * regulation. On connect, replays any events with `sequence_no >
 * sinceSequence` from the durable log so the client never misses an
 * event during a momentary disconnect or page reload.
 */
@Service
class WorkflowEventService(
    private val regulationRepository: RegulationRepository,
    private val eventRepository: WorkflowEventRepository,
    private val sseFanout: SseFanout,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun subscribe(
        regulationId: UUID,
        tenantId: UUID,
        sinceSequence: Long,
        emitter: SseEmitter,
    ) {
        // Tenant guard: never stream events for a regulation outside
        // the caller's tenant. Throws → handled by the controller's
        // exception mapping into 404.
        regulationRepository.findByIdAndTenantId(regulationId, tenantId)
            ?: throw RegulationNotFoundException(regulationId)

        val past = eventRepository
            .findAllByRegulationIdAndTenantIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                regulationId, tenantId, sinceSequence,
            )

        try {
            for (event in past) {
                emitter.send(
                    SseEmitter.event()
                        .id(event.sequenceNo.toString())
                        .name(event.eventType)
                        .data(event.payload),
                )
            }
            // Comment line lets the client confirm the connection is live
            // even before any new event lands.
            emitter.send(SseEmitter.event().comment("subscribed; replayed ${past.size} past events"))
        } catch (e: IOException) {
            log.debug("SSE replay failed for regulation {}: {}", regulationId, e.message)
            emitter.completeWithError(e)
            return
        }

        // Hand off to the live dispatcher for any events fired from now on.
        sseFanout.subscribe(regulationId, emitter)
        log.info(
            "SSE subscriber attached to regulation {} (replayed {}, now active {})",
            regulationId, past.size, sseFanout.activeSubscribers(regulationId),
        )
    }
}
