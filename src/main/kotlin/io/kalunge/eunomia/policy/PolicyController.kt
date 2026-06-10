package io.kalunge.eunomia.policy

import io.kalunge.eunomia.auth.ErrorBody
import io.kalunge.eunomia.tenancy.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/policies")
class PolicyController(
    private val policyService: PolicyService,
) {

    /**
     * Upload an internal policy. The async indexer chunks + embeds in
     * the background; this endpoint returns 201 with status PARSING and
     * chunkCount 0 — the client polls the (forthcoming) detail endpoint
     * to see status transition to INDEXED.
     */
    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @AuthenticationPrincipal userId: UUID,
        @RequestPart("file") file: MultipartFile,
        @RequestPart(name = "title", required = false) title: String?,
    ): ResponseEntity<PolicySummary> {
        val tenantId = TenantContext.requireTenantId()
        val policy = policyService.upload(tenantId, userId, title, file)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(PolicySummary.from(policy, chunkCount = 0))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadInput(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorBody("invalid_input", e.message ?: "Invalid input."))
}
