package io.kalunge.eunomia.policy

import com.fasterxml.jackson.annotation.JsonInclude
import io.kalunge.eunomia.domain.Policy
import io.kalunge.eunomia.domain.PolicyChunk
import io.kalunge.eunomia.domain.PolicyStatus
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PolicySummary(
    val id: UUID,
    val title: String,
    val sourceFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: PolicyStatus,
    val chunkCount: Int,
    val uploadedAt: Instant,
    val processedAt: Instant?,
) {
    companion object {
        fun from(p: Policy, chunkCount: Int) = PolicySummary(
            id = p.id,
            title = p.title,
            sourceFilename = p.sourceFilename,
            mimeType = p.mimeType,
            sizeBytes = p.sizeBytes,
            status = p.status,
            chunkCount = chunkCount,
            uploadedAt = p.uploadedAt,
            processedAt = p.processedAt,
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PolicyDetail(
    val id: UUID,
    val title: String,
    val sourceFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: PolicyStatus,
    val uploadedAt: Instant,
    val processedAt: Instant?,
    val lastError: String?,
    val chunks: List<PolicyChunkView>,
) {
    companion object {
        fun from(p: Policy, chunks: List<PolicyChunk>) = PolicyDetail(
            id = p.id,
            title = p.title,
            sourceFilename = p.sourceFilename,
            mimeType = p.mimeType,
            sizeBytes = p.sizeBytes,
            status = p.status,
            uploadedAt = p.uploadedAt,
            processedAt = p.processedAt,
            lastError = p.lastError,
            chunks = chunks.map { PolicyChunkView.from(it) },
        )
    }
}

data class PolicyChunkView(
    val id: UUID,
    val chunkIndex: Int,
    val text: String,
    val pageStart: Int?,
    val pageEnd: Int?,
) {
    companion object {
        fun from(c: PolicyChunk) = PolicyChunkView(
            id = c.id,
            chunkIndex = c.chunkIndex,
            text = c.text,
            pageStart = c.pageStart,
            pageEnd = c.pageEnd,
        )
    }
}
