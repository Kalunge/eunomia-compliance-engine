package io.kalunge.eunomia.workflow

import io.kalunge.eunomia.domain.Policy
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.PolicyStatus
import io.kalunge.eunomia.embedding.EmbeddingService
import io.kalunge.eunomia.extraction.DocumentParser
import io.kalunge.eunomia.policy.PolicyChunkRow
import io.kalunge.eunomia.policy.PolicyChunkWriter
import io.kalunge.eunomia.policy.PolicyChunker
import io.kalunge.eunomia.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Indexes an uploaded policy: parse → chunk → embed → persist. Status
 * transitions PARSING → INDEXED on success, PARSING → FAILED with a
 * lastError on any throw.
 *
 * Runs out-of-band via @Async (same pattern as ExtractionWorkflow). The
 * upload endpoint returns 201 immediately and the worker takes over
 * once the upload's transaction commits, so the freshly-saved policy
 * row is durably visible by the time the worker queries for it.
 */
@Service
class PolicyIndexWorkflow(
    private val policyRepository: PolicyRepository,
    private val storage: StorageService,
    private val documentParser: DocumentParser,
    private val chunker: PolicyChunker,
    private val embeddingService: EmbeddingService,
    private val chunkWriter: PolicyChunkWriter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun processInBackground(policyId: UUID, tenantId: UUID) {
        try {
            process(policyId, tenantId)
        } catch (e: Exception) {
            log.error("Policy indexing failed for {} (tenant {})", policyId, tenantId, e)
            recordFailure(policyId, tenantId, e)
        }
    }

    private fun process(policyId: UUID, tenantId: UUID) {
        val policy = policyRepository.findByIdAndTenantId(policyId, tenantId)
            ?: error("Policy $policyId vanished between upload and indexing.")

        val started = System.currentTimeMillis()
        log.info("Indexing policy {} ({})", policyId, policy.sourceFilename)

        val parsedText = parseBlob(policy)
        log.debug("Parsed {} characters from {}", parsedText.length, policy.sourceFilename)

        val chunkTexts = chunker.chunk(parsedText)
        log.debug("Chunked into {} windows", chunkTexts.size)

        if (chunkTexts.isEmpty()) {
            markIndexed(policy)
            log.info("Indexing complete for policy {} — empty document, 0 chunks", policyId)
            return
        }

        val embeddings = embeddingService.embedDocuments(chunkTexts.map { it.text })
        log.debug("Embedded {} chunks", embeddings.size)

        val rows = chunkTexts.zip(embeddings) { c, e ->
            PolicyChunkRow(
                id = UUID.randomUUID(),
                tenantId = policy.tenantId,
                policyId = policy.id,
                chunkIndex = c.chunkIndex,
                text = c.text,
                pageStart = c.pageStart,
                pageEnd = c.pageEnd,
                embedding = e,
            )
        }
        persistChunks(rows)
        markIndexed(policy)

        val elapsedMs = System.currentTimeMillis() - started
        log.info(
            "Indexing complete for policy {} — {} chunks in {} ms",
            policyId,
            rows.size,
            elapsedMs,
        )
    }

    private fun parseBlob(policy: Policy): String = storage.read(policy.storageKey).use { stream ->
        documentParser.parse(stream, filenameHint = policy.sourceFilename).text
    }

    @Transactional
    internal fun persistChunks(rows: List<PolicyChunkRow>) {
        chunkWriter.insertAll(rows)
    }

    @Transactional
    internal fun markIndexed(policy: Policy) {
        policyRepository.save(
            clonePolicy(policy, status = PolicyStatus.INDEXED, processedAt = Instant.now()),
        )
    }

    @Transactional
    internal fun recordFailure(policyId: UUID, tenantId: UUID, error: Throwable) {
        val policy = policyRepository.findByIdAndTenantId(policyId, tenantId) ?: return
        policyRepository.save(
            clonePolicy(
                policy,
                status = PolicyStatus.FAILED,
                processedAt = Instant.now(),
                lastError = error.message ?: error.javaClass.simpleName,
            ),
        )
    }

    private fun clonePolicy(
        from: Policy,
        status: PolicyStatus = from.status,
        processedAt: Instant? = from.processedAt,
        lastError: String? = from.lastError,
    ): Policy = Policy(
        id = from.id,
        tenantId = from.tenantId,
        title = from.title,
        sourceFilename = from.sourceFilename,
        storageKey = from.storageKey,
        mimeType = from.mimeType,
        sizeBytes = from.sizeBytes,
        status = status,
        uploadedBy = from.uploadedBy,
        uploadedAt = from.uploadedAt,
        processedAt = processedAt,
        lastError = lastError,
    )
}
