package io.kalunge.eunomia.regulation

import com.fasterxml.jackson.annotation.JsonInclude
import io.kalunge.eunomia.domain.GapStatus
import io.kalunge.eunomia.domain.Obligation
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
        fun from(r: Regulation, obligations: List<Obligation>) = RegulationDetail(
            id = r.id,
            title = r.title,
            sourceFilename = r.sourceFilename,
            mimeType = r.mimeType,
            sizeBytes = r.sizeBytes,
            status = r.status,
            uploadedAt = r.uploadedAt,
            processedAt = r.processedAt,
            lastError = r.lastError,
            obligations = obligations.map { ObligationView.from(it) },
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
) {
    companion object {
        fun from(o: Obligation) = ObligationView(
            id = o.id,
            summary = o.summary,
            sourceExcerpt = o.sourceExcerpt,
            pageStart = o.pageStart,
            pageEnd = o.pageEnd,
            orderIndex = o.orderIndex,
            gapStatus = o.gapStatus,
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
