package io.kalunge.eunomia.regulation

import com.fasterxml.jackson.annotation.JsonInclude
import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.Obligation
import io.kalunge.eunomia.domain.ObligationPolicyMatch
import io.kalunge.eunomia.domain.PolicyChunk
import io.kalunge.eunomia.domain.ProductService
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationStatus
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegulationSummary(
    val id: UUID,
    val title: String,
    val sourceFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: RegulationStatus,
    val obligationCount: Int,
    val gapCount: Int,
    val uploadedAt: Instant,
    val processedAt: Instant?,
) {
    companion object {
        fun from(r: Regulation, obligationCount: Int, gapCount: Int) = RegulationSummary(
            id = r.id,
            title = r.title,
            sourceFilename = r.sourceFilename,
            mimeType = r.mimeType,
            sizeBytes = r.sizeBytes,
            status = r.status,
            obligationCount = obligationCount,
            gapCount = gapCount,
            uploadedAt = r.uploadedAt,
            processedAt = r.processedAt,
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegulationDetail(
    val id: UUID,
    val title: String,
    val sourceFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: RegulationStatus,
    val uploadedAt: Instant,
    val processedAt: Instant?,
    val lastError: String?,
    val obligations: List<ObligationView>,
) {
    companion object {
        fun from(
            r: Regulation,
            obligations: List<Obligation>,
            matchesByObligation: Map<UUID, List<MatchedChunkView>> = emptyMap(),
            impactsByObligation: Map<UUID, List<ImpactedProductView>> = emptyMap(),
        ) = RegulationDetail(
            id = r.id,
            title = r.title,
            sourceFilename = r.sourceFilename,
            mimeType = r.mimeType,
            sizeBytes = r.sizeBytes,
            status = r.status,
            uploadedAt = r.uploadedAt,
            processedAt = r.processedAt,
            lastError = r.lastError,
            obligations = obligations.map {
                ObligationView.from(
                    o = it,
                    matchedChunks = matchesByObligation[it.id].orEmpty(),
                    impactedProducts = impactsByObligation[it.id].orEmpty(),
                )
            },
        )
    }
}

data class ObligationView(
    val id: UUID,
    val summary: String,
    val sourceExcerpt: String,
    val pageStart: Int?,
    val pageEnd: Int?,
    val orderIndex: Int,
    val gapStatus: GapStatus,
    val matchedChunks: List<MatchedChunkView>,
    val impactedProducts: List<ImpactedProductView>,
) {
    companion object {
        fun from(
            o: Obligation,
            matchedChunks: List<MatchedChunkView> = emptyList(),
            impactedProducts: List<ImpactedProductView> = emptyList(),
        ) = ObligationView(
            id = o.id,
            summary = o.summary,
            sourceExcerpt = o.sourceExcerpt,
            pageStart = o.pageStart,
            pageEnd = o.pageEnd,
            orderIndex = o.orderIndex,
            gapStatus = o.gapStatus,
            matchedChunks = matchedChunks,
            impactedProducts = impactedProducts,
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ImpactedProductView(
    val id: UUID,
    val name: String,
) {
    companion object {
        fun from(p: ProductService) = ImpactedProductView(id = p.id, name = p.name)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MatchedChunkView(
    val chunkId: UUID,
    val policyId: UUID,
    val policyTitle: String,
    val chunkIndex: Int,
    val text: String,
    val confidence: Double,
    val reasoning: String?,
) {
    companion object {
        fun from(
            match: ObligationPolicyMatch,
            chunk: PolicyChunk,
            policyTitle: String,
        ) = MatchedChunkView(
            chunkId = chunk.id,
            policyId = chunk.policyId,
            policyTitle = policyTitle,
            chunkIndex = chunk.chunkIndex,
            text = chunk.text,
            confidence = match.confidence,
            reasoning = match.reasoning,
        )
    }
}

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
