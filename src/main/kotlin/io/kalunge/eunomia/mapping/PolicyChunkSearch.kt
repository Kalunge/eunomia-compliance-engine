package io.kalunge.eunomia.mapping

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * pgvector ANN over the policy_chunks table. Sub-linear lookup via the
 * HNSW index from V3, tenant-scoped via the WHERE clause (HNSW does
 * the right thing on the filtered subset).
 *
 * The query vector is rendered as the pgvector textual form
 * "[0.1,0.2,...]" and parsed by Postgres on arrival. Same approach the
 * PolicyChunkWriter uses for inserts.
 */
@Component
class PolicyChunkSearch(private val jdbcTemplate: JdbcTemplate) {

    fun findNearest(tenantId: UUID, queryVector: FloatArray, k: Int): List<ChunkCandidate> {
        if (queryVector.isEmpty() || k <= 0) return emptyList()
        val vecLiteral = vectorLiteral(queryVector)

        return jdbcTemplate.query(
            SEARCH_SQL,
            { rs, _ ->
                ChunkCandidate(
                    chunkId = rs.getObject("id", UUID::class.java),
                    policyId = rs.getObject("policy_id", UUID::class.java),
                    chunkIndex = rs.getInt("chunk_index"),
                    text = rs.getString("text"),
                    distance = rs.getDouble("distance"),
                )
            },
            vecLiteral, tenantId, vecLiteral, k,
        )
    }

    private fun vectorLiteral(v: FloatArray): String =
        v.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }

    companion object {
        private const val SEARCH_SQL = """
            SELECT id, policy_id, chunk_index, text,
                   embedding <=> CAST(? AS vector) AS distance
            FROM policy_chunks
            WHERE tenant_id = ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
        """
    }
}
