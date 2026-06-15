package io.kalunge.eunomia.policy

import io.kalunge.eunomia.auth.ErrorBody
import io.kalunge.eunomia.regulation.PageResponse
import io.kalunge.eunomia.tenancy.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
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
@RequestMapping("/api/v1/policies")
class PolicyController(
    private val policyService: PolicyService,
) {

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

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<PolicySummary> {
        val tenantId = TenantContext.requireTenantId()
        return policyService.list(tenantId, page, size)
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): PolicyDetail {
        val tenantId = TenantContext.requireTenantId()
        return policyService.detail(tenantId, id)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        val tenantId = TenantContext.requireTenantId()
        policyService.delete(tenantId, id)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(PolicyNotFoundException::class)
    fun handleNotFound(e: PolicyNotFoundException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorBody("policy_not_found", "Policy ${e.id} not found."))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadInput(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorBody("invalid_input", e.message ?: "Invalid input."))
}
