package io.kalunge.eunomia.diff

import io.kalunge.eunomia.auth.ErrorBody
import io.kalunge.eunomia.regulation.RegulationNotFoundException
import io.kalunge.eunomia.tenancy.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/diff")
class RegulationDiffController(
    private val regulationDiffService: RegulationDiffService,
) {

    /**
     * Run an obligation-level diff between two regulations of the
     * caller's tenant. Synchronous — blocks for ~40-60s while the
     * agent calls Cohere for embeddings and Groq for per-candidate
     * judgements.
     *
     * Returns the full diff payload on success. The four buckets
     * (unchanged / modified / added / removed) cover every obligation
     * on both sides exhaustively.
     *
     * Status codes:
     *  200 — diff complete; body is RegulationDiff
     *  400 — baselineId and candidateId are the same regulation, or
     *        the request body is malformed
     *  404 — either regulation does not exist in the caller's tenant
     *  409 — either regulation is not in a diffable state (must be
     *        MAPPED, BRIEF_GENERATING, or BRIEF_READY)
     */
    @PostMapping
    fun diff(
        @RequestBody request: RegulationDiffRequest,
    ): ResponseEntity<RegulationDiff> {
        val tenantId = TenantContext.requireTenantId()
        val result = regulationDiffService.diff(tenantId, request.baselineId, request.candidateId)
        return ResponseEntity.ok(result)
    }

    // ── Error mapping ────────────────────────────────────────────────

    @ExceptionHandler(RegulationNotFoundException::class)
    fun handleNotFound(e: RegulationNotFoundException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorBody("regulation_not_found", "Regulation ${e.id} not found."))

    @ExceptionHandler(RegulationNotDiffableException::class)
    fun handleNotDiffable(e: RegulationNotDiffableException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorBody("regulation_not_diffable", e.message ?: "Regulation not in diffable state."))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadInput(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorBody("invalid_input", e.message ?: "Invalid input."))
}
