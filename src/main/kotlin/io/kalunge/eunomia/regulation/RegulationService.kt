package io.kalunge.eunomia.regulation

import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.ObligationImpactRepository
import io.kalunge.eunomia.domain.ObligationPolicyMatchRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.PolicyChunkRepository
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.ProductServiceRepository
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.storage.StorageService
import io.kalunge.eunomia.workflow.ExtractionWorkflow
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
    private val matchRepository: ObligationPolicyMatchRepository,
    private val policyChunkRepository: PolicyChunkRepository,
    private val policyRepository: PolicyRepository,
    private val impactRepository: ObligationImpactRepository,
    private val productServiceRepository: ProductServiceRepository,
    private val storage: StorageService,
    private val extractionWorkflow: ExtractionWorkflow,
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

        // Kick off extraction AFTER the upload's transaction commits.
        // Doing this inline would mean the async worker could find an
        // empty/uncommitted regulation row (since @Async runs in a
        // separate transaction). afterCommit() guarantees the row is
        // visible before the workflow starts.
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                extractionWorkflow.processInBackground(saved.id, tenantId)
            }
        })

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

        // Three batch queries to avoid N+1 — one for matches, one for the
        // referenced chunks, one for the policies those chunks belong to.
        val matches = matchRepository.findAllByObligationIdInAndTenantId(
            obligations.map { it.id },
            tenantId,
        )
        val chunkIdsCited = matches.map { it.policyChunkId }.toSet()
        val chunks = if (chunkIdsCited.isEmpty()) emptyList()
        else policyChunkRepository.findAllByIdInAndTenantId(chunkIdsCited, tenantId)
        val chunkById = chunks.associateBy { it.id }

        val policyIds = chunks.map { it.policyId }.toSet()
        val policyTitleById = if (policyIds.isEmpty()) emptyMap()
        else policyRepository.findAllById(policyIds).associateBy({ it.id }, { it.title })

        val matchesByObligation = matches
            .mapNotNull { m ->
                val chunk = chunkById[m.policyChunkId] ?: return@mapNotNull null
                val title = policyTitleById[chunk.policyId] ?: "Unknown policy"
                m.obligationId to MatchedChunkView.from(m, chunk, title)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, views) -> views.sortedByDescending { it.confidence } }

        // Impact tags — two more batch queries (obligation→impacts then
        // impacts→products) for the same N+1 reason.
        val impacts = impactRepository.findAllByObligationIdInAndTenantId(
            obligations.map { it.id },
            tenantId,
        )
        val productIdsCited = impacts.map { it.productServiceId }.toSet()
        val productsById = if (productIdsCited.isEmpty()) emptyMap()
        else productServiceRepository.findAllByIdInAndTenantId(productIdsCited, tenantId).associateBy { it.id }

        val impactsByObligation = impacts
            .mapNotNull { i ->
                val product = productsById[i.productServiceId] ?: return@mapNotNull null
                i.obligationId to ImpactedProductView.from(product)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, views) -> views.sortedBy { it.name } }

        return RegulationDetail.from(regulation, obligations, matchesByObligation, impactsByObligation)
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
