package io.kalunge.eunomia.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.PolicyChunkRepository
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.PolicyStatus
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

/**
 * End-to-end test of the policy upload → index workflow.
 *
 * Real Tika, real Postgres + Flyway, real pgvector insert via the
 * JdbcTemplate writer, real chunker — only the embedding provider is
 * stubbed (StubEmbeddingService in TestcontainersConfiguration), so we
 * don't burn Cohere quota in CI.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyIndexWorkflowIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var policyRepository: PolicyRepository
    @Autowired private lateinit var policyChunkRepository: PolicyChunkRepository

    private lateinit var token: String
    private lateinit var tenantId: UUID

    @BeforeEach
    fun seed() {
        val slug = "policy-${System.nanoTime()}"
        val tenant = tenantRepository.save(Tenant(name = "Policy $slug", slug = slug))
        tenantId = tenant.id
        token = registerAndGetToken("policy-${System.nanoTime()}@example.com", slug)
    }

    @Test
    fun `upload triggers indexing and persists chunks with embeddings`() {
        val policyText = """
            Internal Operations Policy 4.2 — Agent KYC Procedures

            All agent transactions above KES 70,000 require photo ID capture
            and biometric verification at the cashier window. The procedure
            applies to deposits, withdrawals, and remittances alike.

            Suspicious activity reports must be filed within 24 hours of the
            triggering event with the Financial Reporting Centre. The bank
            shall retain customer identification records for a minimum of
            seven years from the date of the last transaction.
        """.trimIndent()

        val file = MockMultipartFile(
            "file",
            "policy.txt",
            "text/plain",
            policyText.toByteArray(),
        )

        val uploadResp = mockMvc.perform(
            multipart("/api/v1/policies")
                .file(file)
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PARSING"))
            .andExpect(jsonPath("$.chunkCount").value(0))
            .andReturn()

        val policyId = UUID.fromString(
            objectMapper.readTree(uploadResp.response.contentAsString)["id"].asText(),
        )

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(150))
            .untilAsserted {
                val current = policyRepository.findByIdAndTenantId(policyId, tenantId)
                assertNotNull(current, "Policy should still exist")
                assertEquals(
                    PolicyStatus.INDEXED,
                    current!!.status,
                    "Expected INDEXED after workflow, got ${current.status}. Error: ${current.lastError}",
                )
                assertNotNull(current.processedAt, "processedAt should be set")
            }

        val chunks = policyChunkRepository.findAllByPolicyIdAndTenantIdOrderByChunkIndexAsc(policyId, tenantId)
        assertTrue(chunks.isNotEmpty(), "expected at least one chunk to be persisted")
        chunks.forEachIndexed { i, c ->
            assertEquals(i, c.chunkIndex, "chunkIndex should be 0..N strictly increasing")
            assertTrue(c.text.isNotBlank(), "chunk text should not be blank")
        }
    }

    private fun registerAndGetToken(email: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"$email","password":"a-12-char-password","displayName":"Policy User","tenantSlug":"$slug"}""",
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }
}
