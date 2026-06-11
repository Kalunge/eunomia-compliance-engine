package io.kalunge.eunomia.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.model.chat.ChatLanguageModel
import io.kalunge.eunomia.RoutingStubChatLanguageModel
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.ObligationImpactRepository
import io.kalunge.eunomia.domain.ObligationPolicyMatchRepository
import io.kalunge.eunomia.domain.ObligationRepository
import io.kalunge.eunomia.domain.ProductService
import io.kalunge.eunomia.domain.ProductServiceRepository
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

/**
 * End-to-end test of the full saga: upload a policy → INDEXED, upload a
 * regulation → EXTRACTED → MAPPED, with at least one obligation matched
 * back to a policy chunk.
 *
 * The routing stub returns a fixed mapper judgment (candidate 0,
 * satisfied, confidence 0.9), so the Mapper persists a match for every
 * obligation. The point of this test isn't to verify LLM intelligence —
 * it's to verify the wiring works end-to-end through pgvector ANN, the
 * mapper agent, and the persistence layer.
 */
@Import(TestcontainersConfiguration::class, MappingWorkflowIntegrationTest.SagaStubConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MappingWorkflowIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var regulationRepository: RegulationRepository
    @Autowired private lateinit var obligationRepository: ObligationRepository
    @Autowired private lateinit var matchRepository: ObligationPolicyMatchRepository
    @Autowired private lateinit var productServiceRepository: ProductServiceRepository
    @Autowired private lateinit var impactRepository: ObligationImpactRepository

    private lateinit var token: String
    private lateinit var tenantId: UUID

    @BeforeEach
    fun seed() {
        val slug = "saga-${System.nanoTime()}"
        val tenant = tenantRepository.save(Tenant(name = "Saga $slug", slug = slug))
        tenantId = tenant.id
        token = registerAndGetToken("saga-${System.nanoTime()}@example.com", slug)

        // Seed a small product catalogue so the ImpactAnalyzer has
        // something to tag against. Order matches the indices in
        // SagaStubConfig.impactResponse below.
        productServiceRepository.saveAll(
            listOf(
                ProductService(tenantId = tenantId, name = "Agent Banking"),
                ProductService(tenantId = tenantId, name = "Mobile Banking"),
                ProductService(tenantId = tenantId, name = "Retail Deposits"),
            ),
        )
    }

    @Test
    fun `upload policy and regulation produces matches and flips status to MAPPED`() {
        // ── 1. Upload a policy and wait for INDEXED ─────────────────
        val policyText = """
            Internal Policy 4.2 — Agent KYC Procedures
            Agents shall capture identification documents and a biometric
            scan for any transaction at or above KES 50,000. Suspicious
            activity reports are filed with the Financial Reporting Centre
            within 24 hours.
        """.trimIndent()
        val policyFile = MockMultipartFile("file", "policy.txt", "text/plain", policyText.toByteArray())

        val policyResp = mockMvc.perform(
            multipart("/api/v1/policies")
                .file(policyFile)
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        val policyId = UUID.fromString(objectMapper.readTree(policyResp.response.contentAsString)["id"].asText())

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            mockMvc.perform(get("/api/v1/policies/$policyId").header("Authorization", "Bearer $token"))
                .andExpect(jsonPath("$.status").value("INDEXED"))
        }

        // ── 2. Upload a regulation and wait for MAPPED ──────────────
        val regulationText = """
            Section 3.1 — Banks shall ensure agent collects KYC documents
            for transactions exceeding KES 70,000.
            Section 3.3 — Suspicious transactions shall be reported within
            24 hours.
        """.trimIndent()
        val regFile = MockMultipartFile("file", "circular.txt", "text/plain", regulationText.toByteArray())

        val regResp = mockMvc.perform(
            multipart("/api/v1/regulations")
                .file(regFile)
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        val regulationId = UUID.fromString(objectMapper.readTree(regResp.response.contentAsString)["id"].asText())

        await()
            .atMost(Duration.ofSeconds(25))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val r = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
                assertNotNull(r, "Regulation should exist")
                assertEquals(
                    RegulationStatus.BRIEF_READY,
                    r!!.status,
                    "Expected BRIEF_READY, got ${r.status}. lastError=${r.lastError}",
                )
            }

        // ── 3. Verify matches were persisted ────────────────────────
        val obligations = obligationRepository.findAllByRegulationIdAndTenantIdOrderByOrderIndexAsc(regulationId, tenantId)
        assertTrue(obligations.isNotEmpty(), "Expected obligations to be extracted")

        val matches = matchRepository.findAllByObligationIdInAndTenantId(obligations.map { it.id }, tenantId)
        assertTrue(matches.isNotEmpty(), "Expected at least one obligation-policy match to be persisted")
        matches.forEach {
            assertTrue(it.confidence >= 0.6, "Persisted matches should be above the 0.6 threshold")
        }

        // ── 4. Impact tags were also persisted (ImpactAnalyzer ran inline) ─
        val impacts = impactRepository.findAllByObligationIdInAndTenantId(obligations.map { it.id }, tenantId)
        assertTrue(impacts.isNotEmpty(), "Expected at least one impact tag to be persisted")

        // ── 5. Detail endpoint surfaces both matches and impacts ────
        mockMvc.perform(get("/api/v1/regulations/$regulationId").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("BRIEF_READY"))
            .andExpect(jsonPath("$.obligations[0].matchedChunks").isArray)
            .andExpect(jsonPath("$.obligations[0].matchedChunks[0].confidence").exists())
            .andExpect(jsonPath("$.obligations[0].matchedChunks[0].text").exists())
            .andExpect(jsonPath("$.obligations[0].matchedChunks[0].policyTitle").exists())
            .andExpect(jsonPath("$.obligations[0].impactedProducts").isArray)
            .andExpect(jsonPath("$.obligations[0].impactedProducts[0].name").exists())
    }

    private fun registerAndGetToken(email: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"a-12-char-password","displayName":"Saga User","tenantSlug":"$slug"}"""),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }

    @TestConfiguration
    class SagaStubConfig {
        @Bean
        @Primary
        fun sagaStub(): ChatLanguageModel = RoutingStubChatLanguageModel(
            extractorResponse = """
                {
                  "obligations": [
                    {
                      "summary": "Agents must verify customer identity for transactions above KES 70,000.",
                      "sourceExcerpt": "Banks shall ensure agent collects KYC documents for transactions exceeding KES 70,000."
                    },
                    {
                      "summary": "Banks must report suspicious transactions within 24 hours.",
                      "sourceExcerpt": "Suspicious transactions shall be reported within 24 hours."
                    }
                  ]
                }
            """.trimIndent(),
            mapperResponse = """
                {
                  "judgments": [
                    { "candidateIndex": 0, "satisfied": true, "confidence": 0.88, "reasoning": "policy covers the same control" }
                  ]
                }
            """.trimIndent(),
            // Catalogue size is 3 (Agent Banking, Mobile Banking, Retail Deposits)
            // — return indices 0 and 1 so every obligation gets two tags.
            impactResponse = """{ "impactedIndices": [0, 1] }""",
        )
    }
}
