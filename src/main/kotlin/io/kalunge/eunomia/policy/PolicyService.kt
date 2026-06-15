package io.kalunge.eunomia.policy

import io.kalunge.eunomia.domain.Policy
import io.kalunge.eunomia.domain.PolicyChunkRepository
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.PolicyStatus
import io.kalunge.eunomia.regulation.PageResponse
import io.kalunge.eunomia.storage.StorageService
import io.kalunge.eunomia.workflow.PolicyIndexWorkflow
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Business operations on internal policies. Mirrors RegulationService's
 * shape — controller does HTTP, storage handles bytes, this service
 * orchestrates entity persistence and async indexing kick-off.
 */
@Service
class PolicyService(
    private val policyRepository: PolicyRepository,
    private val policyChunkRepository: PolicyChunkRepository,
    private val storage: StorageService,
    private val policyIndexWorkflow: PolicyIndexWorkflow,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(
        tenantId: UUID,
        uploaderId: UUID,
        title: String?,
        file: MultipartFile,
    ): Policy {
        require(file.size > 0) { "Uploaded file is empty." }
        require(file.contentType in ACCEPTED_MIME_TYPES) {
            "Unsupported file type ${file.contentType}. Accepted: $ACCEPTED_MIME_TYPES"
        }

        val stored = storage.store(file, tenantId)
        val policy = Policy(
            tenantId = tenantId,
            title = (title ?: file.originalFilename ?: "Untitled").take(MAX_TITLE_LENGTH),
            sourceFilename = file.originalFilename ?: "unnamed",
            storageKey = stored.key,
            mimeType = file.contentType!!,
            sizeBytes = stored.sizeBytes,
            status = PolicyStatus.PARSING,
            uploadedBy = uploaderId,
        )
        val saved = policyRepository.save(policy)
        log.info("Stored policy id={} tenantId={} bytes={}", saved.id, tenantId, stored.sizeBytes)

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                policyIndexWorkflow.processInBackground(saved.id, tenantId)
            }
        })

        return saved
    }

    @Transactional(readOnly = true)
    fun list(tenantId: UUID, page: Int, size: Int): PageResponse<PolicySummary> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val results = policyRepository.findAllByTenantIdOrderByUploadedAtDesc(tenantId, pageable)

        val summaries = results.content.map { p ->
            val chunkCount = policyChunkRepository.countByPolicyIdAndTenantId(p.id, tenantId).toInt()
            PolicySummary.from(p, chunkCount)
        }
        return PageResponse(
            items = summaries,
            page = results.number,
            size = results.size,
            totalElements = results.totalElements,
            totalPages = results.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun detail(tenantId: UUID, id: UUID): PolicyDetail {
        val policy = policyRepository.findByIdAndTenantId(id, tenantId)
            ?: throw PolicyNotFoundException(id)
        val chunks = policyChunkRepository.findAllByPolicyIdAndTenantIdOrderByChunkIndexAsc(id, tenantId)
        return PolicyDetail.from(policy, chunks)
    }

    @Transactional
    fun delete(tenantId: UUID, id: UUID) {
        val policy = policyRepository.findByIdAndTenantId(id, tenantId)
            ?: throw PolicyNotFoundException(id)
        // Cascade in V3 takes care of policy_chunks rows. We also delete
        // the blob — it's not referenced anywhere else.
        policyRepository.delete(policy)
        runCatching { storage.delete(policy.storageKey) }
            .onFailure { log.warn("Could not remove blob {} for deleted policy {}", policy.storageKey, id, it) }
        log.info("Deleted policy id={} tenantId={}", id, tenantId)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 240

        private val ACCEPTED_MIME_TYPES = setOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
        )
    }
}

class PolicyNotFoundException(val id: UUID) : RuntimeException("Policy $id not found in this tenant.")
