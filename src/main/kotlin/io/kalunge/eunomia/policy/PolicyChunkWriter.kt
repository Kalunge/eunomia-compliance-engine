package io.kalunge.eunomia.policy

import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Types
import java.util.UUID

/**
 * JdbcTemplate-backed insert for policy_chunks. JPA can't natively map
 * pgvector(1024), so we hand-write the INSERT to include both the
 * relational columns and the embedding in one round trip.
 *
 * The embedding is rendered as the textual form pgvector expects
 * ("[0.1,0.2,...]") and cast with ::vector. This is the standard way
 * to insert vectors over plain JDBC without a pgvector-specific driver.
 */
@Component
class PolicyChunkWriter(private val jdbcTemplate: JdbcTemplate) {

    fun insertAll(chunks: List<PolicyChunkRow>) {
        if (chunks.isEmpty()) return
        jdbcTemplate.batchUpdate(
            INSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun setValues(ps: PreparedStatement, i: Int) = bind(ps, chunks[i])
                override fun getBatchSize() = chunks.size
            },
        )
    }

    private fun bind(ps: PreparedStatement, row: PolicyChunkRow) {
        ps.setObject(1, row.id)
        ps.setObject(2, row.tenantId)
        ps.setObject(3, row.policyId)
        ps.setInt(4, row.chunkIndex)
        ps.setString(5, row.text)
        if (row.pageStart != null) ps.setInt(6, row.pageStart) else ps.setNull(6, Types.INTEGER)
        if (row.pageEnd != null) ps.setInt(7, row.pageEnd) else ps.setNull(7, Types.INTEGER)
        ps.setString(8, vectorLiteral(row.embedding))
    }

    private fun vectorLiteral(v: FloatArray): String =
        v.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }

    companion object {
        private const val INSERT_SQL = """
            INSERT INTO policy_chunks
                (id, tenant_id, policy_id, chunk_index, text, page_start, page_end, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector)
        """
    }
}

data class PolicyChunkRow(
    val id: UUID,
    val tenantId: UUID,
    val policyId: UUID,
    val chunkIndex: Int,
    val text: String,
    val pageStart: Int?,
    val pageEnd: Int?,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = id.hashCode()
}
