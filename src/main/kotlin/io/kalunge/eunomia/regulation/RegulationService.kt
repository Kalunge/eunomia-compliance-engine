package io.kalunge.eunomia.regulation

import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Business operations on regulations. Stays thin: the controller does
 * HTTP, the storage service handles bytes, this service orchestrates
 * the domain entities and (next chunk) kicks off the agent workflow.
 *
 * Every method takes [tenantId] as the first parameter. No cross-tenant
 * access is possible by construction — repositories enforce the same
 * filter at the JPA layer.
 */
@Service
class RegulationService(
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
    private val storage: StorageService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(
        tenantId: UUID,
        uploaderId: UUID,
        title: String?,
        file: MultipartFile,
    ): Regulation {
        require(file.size > 0) { "Uploaded file is empty." }
        require(file.contentType in ACCEPTED_MIME_TYPES) {
            "Unsupported file type ${file.contentType}. Accepted: $ACCEPTED_MIME_TYPES"
        }

        val stored = storage.store(file, tenantId)
        val regulation = Regulation(
            tenantId = tenantId,
            title = (title ?: file.originalFilename ?: "Untitled").take(MAX_TITLE_LENGTH),
            sourceFilename = file.originalFilename ?: "unnamed",
            storageKey = stored.key,
            mimeType = file.contentType!!,
            sizeBytes = stored.sizeBytes,
            status = RegulationStatus.PARSING,
            uploadedBy = uploaderId,
        )
        val saved = regulationRepository.save(regulation)
        log.info("Stored regulation id={} tenantId={} bytes={}", saved.id, tenantId, stored.sizeBytes)
        return saved
    }

    @Transactional(readOnly = true)
    fun list(tenantId: UUID, page: Int, size: Int): PageResponse<RegulationSummary> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val results = regulationRepository.findAllByTenantIdOrderByUploadedAtDesc(tenantId, pageable)

        val summaries = results.content.map { r ->
            val obligations = obligationRepository.findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(r.id, tenantId)
            val gaps = obligations.count { it.gapStatus == GapStatus.GAP }
            RegulationSummary.from(r, obligationCount = obligations.size, gapCount = gaps)
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
    fun detail(tenantId: UUID, id: UUID): RegulationDetail {
        val regulation = regulationRepository.findByIdAndTenantId(id, tenantId)
            ?: throw RegulationNotFoundException(id)
        val obligations = obligationRepository.findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(id, tenantId)
        return RegulationDetail.from(regulation, obligations)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 240

        // Permissive on purpose for Sprint 1; Tika's content-type detection
        // (next chunk) will harden this against MIME spoofing.
        private val ACCEPTED_MIME_TYPES = setOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/msword", // .doc
            "text/plain",
            "text/markdown",
            "text/x-markdown",
        )
    }
}

class RegulationNotFoundException(val id: UUID) : RuntimeException("Regulation $id not found in this tenant.")
