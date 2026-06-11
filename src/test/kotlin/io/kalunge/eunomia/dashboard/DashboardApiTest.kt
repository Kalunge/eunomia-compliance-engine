package io.kalunge.eunomia.dashboard

import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.model.chat.ChatLanguageModel
import io.kalunge.eunomia.RoutingStubChatLanguageModel
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantMetricsRepository
import io.kalunge.eunomia.domain.TenantRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
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
 * End-to-end test of the dashboard endpoint plus the synchronous
 * recompute hook in MappingWorkflow.
 *
 * A real Tika parse, a real Mapper run (via the stub chat model), a
 * real Awaitility-polled saga, a real pgvector chunk insert — only
 * the LLM is faked.
 */
@Import(TestcontainersConfiguration::class, DashboardApiTest.DashboardStubConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var regulationRepository: RegulationRepository
    @Autowired private lateinit var metricsRepository: TenantMetricsRepository

    private lateinit var token: String
    private lateinit var tenantId: UUID

    @BeforeEach
    fun seed() {
        val slug = "dash-${System.nanoTime()}"
        val tenant = tenantRepository.save(Tenant(name = "Dash $slug", slug = slug))
        tenantId = tenant.id
        token = registerAndGetToken("dash-${System.nanoTime()}@example.com", slug)
    }

    @Test
    fun `fresh tenant returns zeroed metrics`() {
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.regulationsProcessed").value(0))
            .andExpect(jsonPath("$.openGaps").value(0))
            .andExpect(jsonPath("$.impactedProductCount").value(0))
            .andExpect(jsonPath("$.recentActivity").isArray)
            .andExpect(jsonPath("$.recentActivity.length()").value(0))
    }

    @Test
    fun `upload triggers recompute hook and metrics reflect the new regulation`() {
        val regulationText = """
            Section 3.1 — Banks shall verify customer identity for transactions above KES 70,000.
            Section 3.3 — Suspicious transactions shall be reported within 24 hours.
        """.trimIndent()
        val file = MockMultipartFile("file", "circular.txt", "text/plain", regulationText.toByteArray())

        val uploadResp = mockMvc.perform(
            multipart("/api/v1/regulations").file(file).header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        val regulationId = UUID.fromString(
            objectMapper.readTree(uploadResp.response.contentAsString)["id"].asText(),
        )

        // Wait for the full saga (PARSING → EXTRACTED → MAPPED), which
        // includes the metrics recompute hook.
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val r = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
            assertEquals(RegulationStatus.MAPPED, r?.status, "expected MAPPED, got ${r?.status}")
        }

        // Verify the hook populated the row in the DB directly.
        val metrics = metricsRepository.findById(tenantId).orElse(null)
        assert(metrics != null) { "Expected tenant_metrics row to exist after MAPPED" }
        assert(metrics!!.regulationsProcessed >= 1) {
            "Expected regulationsProcessed ≥ 1, got ${metrics.regulationsProcessed}"
        }

        // The endpoint surfaces the same numbers + the recent-activity feed.
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.regulationsProcessed").value(1))
            .andExpect(jsonPath("$.openGaps").value(2)) // both obligations → gaps (no policies indexed)
            .andExpect(jsonPath("$.recentActivity.length()").value(1))
            .andExpect(jsonPath("$.recentActivity[0].title").exists())
            .andExpect(jsonPath("$.recentActivity[0].obligationCount").value(2))
    }

    @Test
    fun `unauthenticated dashboard request is rejected with 401`() {
        mockMvc.perform(get("/api/v1/dashboard"))
            .andExpect(status().isUnauthorized)
    }

    private fun registerAndGetToken(email: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"$email","password":"a-12-char-password","displayName":"Dash User","tenantSlug":"$slug"}""",
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }

    @TestConfiguration
    class DashboardStubConfig {
        @Bean
        @Primary
        fun dashStub(): ChatLanguageModel = RoutingStubChatLanguageModel(
            extractorResponse = """
                {
                  "obligations": [
                    { "summary": "Verify customer identity above KES 70,000.",
                      "sourceExcerpt": "Banks shall verify customer identity for transactions above KES 70,000." },
                    { "summary": "Report suspicious transactions within 24 hours.",
                      "sourceExcerpt": "Suspicious transactions shall be reported within 24 hours." }
                  ]
                }
            """.trimIndent(),
            mapperResponse = """{ "judgments": [] }""",
            impactResponse = """{ "impactedIndices": [] }""",
        )
    }
}
