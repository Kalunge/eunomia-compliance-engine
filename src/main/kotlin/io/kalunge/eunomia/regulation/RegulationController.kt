package io.kalunge.eunomia.regulation

import io.kalunge.eunomia.auth.ErrorBody
import io.kalunge.eunomia.tenancy.TenantContext
import org.springframework.http.HttpStatus
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

    // ── Error mapping ────────────────────────────────────────────────

    @ExceptionHandler(RegulationNotFoundException::class)
    fun handleNotFound(e: RegulationNotFoundException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorBody("regulation_not_found", "Regulation ${e.id} not found."))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadInput(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorBody("invalid_input", e.message ?: "Invalid input."))
}
