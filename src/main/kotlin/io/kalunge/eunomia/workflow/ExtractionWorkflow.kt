package io.kalunge.eunomia.workflow

import io.kalunge.eunomia.domain.Obligation
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.extraction.DocumentParser
import io.kalunge.eunomia.extraction.ExtractorAgent
import io.kalunge.eunomia.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Runs the Extractor agent against an uploaded regulation, persists the
 * obligations, and updates the regulation status. Sprint 1 ships the
 * first step of the multi-agent saga; later sprints layer Mapper,
 * ImpactAnalyzer, and Summarizer agents into the same workflow shape.
 *
 * @Async lets the upload endpoint return 201 immediately; the workflow
 * runs in Spring's default task executor. Tenant context is NOT
 * propagated automatically across async threads — every method takes
 * tenantId as an explicit parameter so we never accidentally read or
 * write outside the right tenant boundary.
 *
 * Each phase is its own @Transactional unit so a failure persisting
 * obligations doesn't leave the regulation stuck in PARSING. The
 * status field is the saga's persisted state.
 */
@Service
class ExtractionWorkflow(
    private val regulationRepository: RegulationRepository,
    private val obligationRepository: ObligationRepository,
    private val storage: StorageService,
    private val documentParser: DocumentParser,
    private val extractorAgent: ExtractorAgent,
    private val mappingWorkflow: MappingWorkflow,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun processInBackground(regulationId: UUID, tenantId: UUID) {
        try {
            process(regulationId, tenantId)
        } catch (e: Exception) {
            // The processed steps are committed individually so even on
            // failure the row reflects WHAT failed. Log here so it
            // surfaces in container logs alongside the regulation ID.
            log.error("Workflow failed for regulation {} (tenant {})", regulationId, tenantId, e)
            recordFailure(regulationId, tenantId, e)
        }
    }

    private fun process(regulationId: UUID, tenantId: UUID) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
            ?: error("Regulation $regulationId vanished between upload and extraction.")

        val started = System.currentTimeMillis()
        log.info("Extracting regulation {} ({})", regulationId, regulation.sourceFilename)

        val parsedText = parseBlob(regulation)
        log.debug("Parsed {} characters from {}", parsedText.length, regulation.sourceFilename)

        val extracted = extractorAgent.extract(parsedText)
        log.debug("LLM returned {} obligations", extracted.size)

        persistObligations(regulation, extracted)
        markExtracted(regulation)

        val elapsedMs = System.currentTimeMillis() - started
        log.info(
            "Extraction complete for regulation {} — {} obligations in {} ms",
            regulationId,
            extracted.size,
            elapsedMs,
        )

        // Hand off to the Mapper. processInBackground is @Async on
        // MappingWorkflow (different bean), so this returns immediately
        // and the mapping runs on a fresh executor task.
        mappingWorkflow.processInBackground(regulationId, tenantId)
    }

    private fun parseBlob(regulation: Regulation): String {
        return storage.read(regulation.storageKey).use { stream ->
            documentParser.parse(stream, filenameHint = regulation.sourceFilename).text
        }
    }

    @Transactional
    private fun persistObligations(
        regulation: Regulation,
        extracted: List<io.kalunge.eunomia.extraction.ExtractedObligation>,
    ) {
        if (extracted.isEmpty()) return
        val rows = extracted.mapIndexed { index, e ->
            Obligation(
                tenantId = regulation.tenantId,
                regulationId = regulation.id,
                summary = e.summary,
                sourceExcerpt = e.sourceExcerpt,
                pageStart = e.pageStart,
                pageEnd = e.pageEnd,
                orderIndex = index,
            )
        }
        obligationRepository.saveAll(rows)
    }

    @Transactional
    private fun markExtracted(regulation: Regulation) {
        // JPA's dirty-checking won't help here because Regulation is
        // constructed immutably with `val`. Re-save with the new fields.
        regulationRepository.save(
            cloneRegulation(regulation, status = RegulationStatus.EXTRACTED, processedAt = Instant.now()),
        )
    }

    @Transactional
    private fun recordFailure(regulationId: UUID, tenantId: UUID, error: Throwable) {
        val regulation = regulationRepository.findByIdAndTenantId(regulationId, tenantId) ?: return
        regulationRepository.save(
            cloneRegulation(
                regulation,
                status = RegulationStatus.FAILED,
                processedAt = Instant.now(),
                lastError = error.message ?: error.javaClass.simpleName,
            ),
        )
    }

    private fun cloneRegulation(
        from: Regulation,
        status: RegulationStatus = from.status,
        processedAt: Instant? = from.processedAt,
        lastError: String? = from.lastError,
    ): Regulation = Regulation(
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
