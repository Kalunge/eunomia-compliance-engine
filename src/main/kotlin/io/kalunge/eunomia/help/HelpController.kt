package io.kalunge.eunomia.help

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/v1/help")
class HelpController(
    private val helpService: HelpService,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Streams an answer to a question about Eunomia. The body is a
     * `HelpAskRequest`; the response is text/event-stream where each
     * `data:` line is one token chunk from the LLM and the stream
     * ends with an `event: done` line. On failure, an `event: error`
     * line is emitted with the message before the stream closes.
     *
     * The endpoint runs the streaming generation on a dedicated
     * executor so the servlet container's thread isn't held for the
     * full response duration; SseEmitter is thread-safe for send().
     */
    @PostMapping("/ask", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun ask(@RequestBody request: HelpAskRequest): SseEmitter {
        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)

        executor.submit {
            try {
                helpService.streamAnswer(
                    question = request.question,
                    onToken = { token ->
                        try {
                            // JSON-wrap the token so leading whitespace
                            // survives the SSE protocol's `data:` line
                            // parser (which strips one leading space per
                            // the spec). LLM tokens are typically emitted
                            // as " word" — without JSON wrapping the
                            // spaces are eaten and words concatenate.
                            emitter.send(
                                SseEmitter.event()
                                    .data(objectMapper.writeValueAsString(mapOf("t" to token))),
                            )
                        } catch (e: Exception) {
                            log.debug("Failed to send token (client disconnect?): {}", e.message)
                        }
                    },
                    onComplete = {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("{}"))
                        } catch (e: Exception) {
                            log.debug("Failed to send done event: {}", e.message)
                        }
                        emitter.complete()
                    },
                    onError = { error ->
                        try {
                            val msg = error.message ?: error.javaClass.simpleName
                            emitter.send(
                                SseEmitter.event()
                                    .name("error")
                                    .data(objectMapper.writeValueAsString(mapOf("m" to msg))),
                            )
                        } catch (e: Exception) {
                            log.debug("Failed to send error event: {}", e.message)
                        }
                        emitter.complete()
                    },
                )
            } catch (e: Exception) {
                log.warn("Help streaming pipeline crashed: {}", e.message)
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    companion object {
        // 2-minute cap. Help answers normally complete in 2-10 seconds;
        // the cap exists to bound abandoned-tab resource use.
        private const val EMITTER_TIMEOUT_MS = 2L * 60L * 1000L

        private val executor = Executors.newCachedThreadPool { r ->
            Thread(r, "help-stream").apply { isDaemon = true }
        }
    }
}
