package io.kalunge.eunomia.dashboard

import com.fasterxml.jackson.annotation.JsonInclude
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationStatus
import java.time.Instant
import java.util.UUID

data class DashboardSummary(
    val regulationsProcessed: Long,
    val openGaps: Long,
    val impactedProductCount: Long,
    val recentActivity: List<RecentRegulationView>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecentRegulationView(
    val id: UUID,
    val title: String,
    val status: RegulationStatus,
    val obligationCount: Int,
    val gapCount: Int,
    val uploadedAt: Instant,
    val processedAt: Instant?,
) {
    companion object {
        fun from(r: Regulation, obligationCount: Int, gapCount: Int) = RecentRegulationView(
            id = r.id,
            title = r.title,
            status = r.status,
            obligationCount = obligationCount,
            gapCount = gapCount,
            uploadedAt = r.uploadedAt,
            processedAt = r.processedAt,
        )
    }
}
