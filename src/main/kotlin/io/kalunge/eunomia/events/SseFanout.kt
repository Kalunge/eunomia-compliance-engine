package io.kalunge.eunomia.events

import io.kalunge.eunomia.domain.WorkflowEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process fan-out for live workflow events. Subscribers register
 * an [SseEmitter] keyed by `regulation_id`; publishers call
 * [dispatch] and every emitter on the matching key receives the
 * event.
 *
 * `CopyOnWriteArrayList` makes the per-regulation subscriber list
 * cheap to iterate (each dispatch is read-only) at the cost of
 * occasional list copies when subscribers connect/disconnect — fine
 * at our scale, where each user typically has at most one tab open
 * per regulation.
 *
 * Dead emitters (timeouts, closed connections) are pruned on the
 * next dispatch attempt; the emitter's own lifecycle callbacks also
 * remove the entry eagerly.
 */
@Component
class SseFanout {

    private val log = LoggerFactory.getLogger(javaClass)

    private val subscribers: MutableMap<UUID, CopyOnWriteArrayList<SseEmitter>> = ConcurrentHashMap()

    fun subscribe(regulationId: UUID, emitter: SseEmitter) {
        val list = subscribers.computeIfAbsent(regulationId) { CopyOnWriteArrayList() }
        list.add(emitter)
        log.debug("SSE subscriber attached to {} ({} total)", regulationId, list.size)

        val detach = Runnable {
            list.remove(emitter)
            if (list.isEmpty()) subscribers.remove(regulationId, list)
            log.debug("SSE subscriber detached from {} ({} remain)", regulationId, list.size)
        }
        emitter.onCompletion(detach)
        emitter.onTimeout {
            detach.run()
            runCatching { emitter.complete() }
        }
        emitter.onError { detach.run() }
    }

    fun dispatch(event: WorkflowEvent) {
        val list = subscribers[event.regulationId] ?: return
        val dead = mutableListOf<SseEmitter>()
        for (emitter in list) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .id(event.sequenceNo.toString())
                        .name(event.eventType)
                        .data(event.payload),
                )
            } catch (e: Exception) {
                log.debug("SSE dispatch failed for {} — emitter will be pruned: {}", event.regulationId, e.message)
                dead.add(emitter)
            }
        }
        if (dead.isNotEmpty()) list.removeAll(dead.toSet())
    }

    fun activeSubscribers(regulationId: UUID): Int = subscribers[regulationId]?.size ?: 0
}
