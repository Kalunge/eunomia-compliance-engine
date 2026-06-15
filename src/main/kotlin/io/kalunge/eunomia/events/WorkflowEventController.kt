package io.kalunge.eunomia.events

import io.jsonwebtoken.JwtException
import io.kalunge.eunomia.auth.JwtService
import io.kalunge.eunomia.regulation.RegulationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

/**
 * SSE endpoint: GET /api/v1/regulations/{id}/events
 *
 * Auth: JWT in the `token` query parameter. EventSource can't send
 * custom headers, so the standard pick is a URL-bound token. The
 * route is permitted in SecurityConfig and authenticates itself by
 * calling JwtService.verify(...).
 *
 * Reconnection: if the client passes `Last-Event-ID` (set by the
 * browser's EventSource on auto-reconnect) or `?lastEventId`, we
 * replay events strictly newer than that sequence number before
 * subscribing to live events. No double-delivery, no gaps.
 */
@RestController
@RequestMapping("/api/v1/regulations")
class WorkflowEventController(
    private val workflowEventService: WorkflowEventService,
    private val jwtService: JwtService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping(value = ["/{id}/events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable id: UUID,
        @RequestParam("token") token: String,
        @RequestParam(name = "lastEventId", required = false) lastEventIdParam: String?,
        @RequestHeader(name = "Last-Event-ID", required = false) lastEventIdHeader: String?,
    ): SseEmitter {
        val claims = try {
            jwtService.verify(token)
        } catch (e: JwtException) {
            log.debug("SSE auth failed for regulation {}: {}", id, e.message)
            throw SseUnauthorizedException("invalid or expired token")
        }

        val sinceSeq = (lastEventIdParam ?: lastEventIdHeader)?.toLongOrNull() ?: 0L

        // 1-hour timeout: long enough for the user to leave a tab
        // open during the entire saga + brief generation + reading
        // time, short enough that abandoned tabs eventually free up
        // the emitter slot.
        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)
        workflowEventService.subscribe(id, claims.tenantId, sinceSeq, emitter)
        return emitter
    }

    @ExceptionHandler(RegulationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(): String = "regulation not found"

    @ExceptionHandler(SseUnauthorizedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUnauthorized(e: SseUnauthorizedException): String = e.message ?: "unauthorized"

    companion object {
        private const val EMITTER_TIMEOUT_MS = 60L * 60L * 1000L
    }
}

class SseUnauthorizedException(message: String) : RuntimeException(message)
