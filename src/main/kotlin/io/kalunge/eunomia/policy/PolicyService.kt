package io.kalunge.eunomia.policy

import io.kalunge.eunomia.domain.Policy
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.PolicyStatus
import io.kalunge.eunomia.storage.StorageService
import io.kalunge.eunomia.workflow.PolicyIndexWorkflow
import org.slf4j.LoggerFactory
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

        // Kick off indexing AFTER commit so the @Async worker doesn't
        // race the upload's transaction — same reasoning as the
        // ExtractionWorkflow trigger in RegulationService.upload.
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                policyIndexWorkflow.processInBackground(saved.id, tenantId)
            }
        })

        return saved
    }

    companion object {
        const val MAX_TITLE_LENGTH = 240

        // Same accepted set as regulations — policies are uploaded the
        // same way (PDF, DOCX, plain text, markdown).
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
