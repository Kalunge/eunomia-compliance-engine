package io.kalunge.eunomia.help

/**
 * Request body for POST /api/v1/help/ask. The endpoint streams the
 * LLM's response back over SSE, so there's no synchronous response
 * DTO — tokens arrive as `data:` events and the stream terminates
 * with an `event: done` line.
 */
data class HelpAskRequest(
    val question: String,
)
