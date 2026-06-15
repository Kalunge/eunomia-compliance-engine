package io.kalunge.eunomia.regulation

import io.kalunge.eunomia.auth.ErrorBody
import io.kalunge.eunomia.tenancy.TenantContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/regulations")
class RegulationController(
    private val regulationService: RegulationService,
) {

    /**
     * Upload a regulatory document. The optional `title` form field
     * lets the user name the document; if omitted we use the filename.
     */
    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @AuthenticationPrincipal userId: UUID,
        @RequestPart("file") file: MultipartFile,
        @RequestPart(name = "title", required = false) title: String?,
    ): ResponseEntity<RegulationSummary> {
        val tenantId = TenantContext.requireTenantId()
        val regulation = regulationService.upload(tenantId, userId, title, file)
        // Newly uploaded — no obligations or gaps yet (extraction runs async).
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(RegulationSummary.from(regulation, obligationCount = 0, gapCount = 0))
    }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<RegulationSummary> {
        val tenantId = TenantContext.requireTenantId()
        return regulationService.list(tenantId, page, size)
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): RegulationDetail {
        val tenantId = TenantContext.requireTenantId()
        return regulationService.detail(tenantId, id)
    }

    /**
     * Regenerate the impact brief. Returns 202 Accepted; the client
     * polls the detail endpoint to see status flip
     * MAPPED → BRIEF_GENERATING → BRIEF_READY (or back to MAPPED on
     * failure with `lastError` populated).
     */
    @PostMapping("/{id}/brief")
    fun regenerateBrief(@PathVariable id: UUID): ResponseEntity<Void> {
        val tenantId = TenantContext.requireTenantId()
        regulationService.regenerateBrief(tenantId, id)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @GetMapping("/{id}/brief.md", produces = ["text/markdown;charset=UTF-8"])
    fun briefMarkdown(@PathVariable id: UUID): ResponseEntity<String> {
        val tenantId = TenantContext.requireTenantId()
        val markdown = regulationService.briefMarkdown(tenantId, id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .body(markdown)
    }

    @GetMapping("/{id}/brief.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun briefPdf(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val tenantId = TenantContext.requireTenantId()
        val pdfBytes = regulationService.briefPdf(tenantId, id)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"impact-brief.pdf\"")
            .body(pdfBytes)
    }

    // ── Error mapping ────────────────────────────────────────────────

    @ExceptionHandler(RegulationNotFoundException::class)
    fun handleNotFound(e: RegulationNotFoundException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorBody("regulation_not_found", "Regulation ${e.id} not found."))

    @ExceptionHandler(BriefRegenerateConflictException::class)
    fun handleBriefConflict(e: BriefRegenerateConflictException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorBody("brief_regenerate_conflict", e.message ?: "Workflow in flight."))

    @ExceptionHandler(BriefNotReadyException::class)
    fun handleBriefNotReady(e: BriefNotReadyException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorBody("brief_not_ready", e.message ?: "Brief not generated yet."))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadInput(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorBody("invalid_input", e.message ?: "Invalid input."))
}
