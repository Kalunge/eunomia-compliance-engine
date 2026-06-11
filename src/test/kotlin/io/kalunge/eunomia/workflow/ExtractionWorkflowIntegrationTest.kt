package io.kalunge.eunomia.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.model.chat.ChatLanguageModel
import io.kalunge.eunomia.RoutingStubChatLanguageModel
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import io.kalunge.eunomia.extraction.StubChatLanguageModel
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

/**
 * End-to-end test of the upload → Extractor workflow path.
 *
 * Uses a custom stub model that returns a richer obligations payload
 * (the default stub in TestcontainersConfiguration returns []), so we
 * can assert obligations were persisted and the status moved to
 * EXTRACTED. Real Tika, real Postgres (Testcontainers), real
 * Flyway-applied schema — only the LLM call is faked.
 */
@Import(TestcontainersConfiguration::class, ExtractionWorkflowIntegrationTest.RichStubConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExtractionWorkflowIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var regulationRepository: RegulationRepository

    private lateinit var token: String
    private lateinit var tenantId: UUID

    @BeforeEach
    fun seed() {
        val slug = "wf-${System.nanoTime()}"
        val tenant = tenantRepository.save(Tenant(name = "WF $slug", slug = slug))
        tenantId = tenant.id
        token = registerAndGetToken("wf-user-${System.nanoTime()}@example.com", slug)
    }

    @Test
    fun `upload triggers extraction and persists obligations`() {
        val regulatoryText = """
            Section 3.1 Customer Identification at Agent Outlets

            Agents shall verify identity for any transaction exceeding KES 70,000.
            Suspicious transactions must be reported to the FRC within 24 hours.
        """.trimIndent()

        val file = MockMultipartFile(
            "file",
            "circular.txt",
            "text/plain",
            regulatoryText.toByteArray(),
        )

        // 1. Upload — synchronous part returns 201 with PARSING.
        val uploadResp = mockMvc.perform(
            multipart("/api/v1/regulations")
                .file(file)
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PARSING"))
            .andReturn()

        val regulationId = UUID.fromString(
            objectMapper.readTree(uploadResp.response.contentAsString)["id"].asText(),
        )

        // 2. The async workflow fires after the upload's transaction
        //    commits. The full saga is Extractor → Mapper → Summarizer
        //    so the terminal state is BRIEF_READY. With no policy
        //    chunks indexed in this tenant, every obligation will be
        //    flagged GAP.
        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofMillis(150))
            .untilAsserted {
                val current = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
                assertNotNull(current, "Regulation should still exist")
                assertEquals(
                    RegulationStatus.BRIEF_READY,
                    current!!.status,
                    "Expected BRIEF_READY after full saga, got ${current.status}. Error: ${current.lastError}",
                )
                assertNotNull(current.processedAt, "processedAt should be set")
            }

        // 3. Detail endpoint now shows the extracted obligations, all
        //    of them flagged GAP (no policies were uploaded in this
        //    tenant), plus the rendered brief.
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/regulations/$regulationId")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("BRIEF_READY"))
            .andExpect(jsonPath("$.briefMarkdown").exists())
            .andExpect(jsonPath("$.obligations.length()").value(2))
            .andExpect(jsonPath("$.obligations[0].summary").value("Agents must verify customer identity for transactions above KES 70,000."))
            .andExpect(jsonPath("$.obligations[0].orderIndex").value(0))
            .andExpect(jsonPath("$.obligations[0].gapStatus").value("GAP"))
            .andExpect(jsonPath("$.obligations[1].summary").value("Banks must report suspicious transactions to the FRC within 24 hours."))
            .andExpect(jsonPath("$.obligations[1].gapStatus").value("GAP"))
    }

    @Test
    fun `workflow records FAILED when the LLM returns unparseable output`() {
        // Local stub that returns nonsense — drives the failure path.
        val brokenStub = StubChatLanguageModel("Sorry, I can't help with that.")
        val workflow = ExtractionWorkflow(
            regulationRepository = regulationRepository,
            obligationRepository = autowiredObligationRepo(),
            storage = autowiredStorage(),
            documentParser = autowiredParser(),
            extractorAgent = io.kalunge.eunomia.extraction.LangChain4jExtractorAgent(brokenStub, objectMapper),
            mappingWorkflow = autowiredMappingWorkflow(),
        )

        // Manually upload through the API to get a real regulation row,
        // then drive the workflow directly with the broken extractor.
        val pdf = MockMultipartFile("file", "broken.txt", "text/plain", "Some bytes.".toByteArray())
        val uploadResp = mockMvc.perform(
            multipart("/api/v1/regulations")
                .file(pdf)
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        val regulationId = UUID.fromString(
            objectMapper.readTree(uploadResp.response.contentAsString)["id"].asText(),
        )

        // Wait for the default workflow to finish (the chained Mapper +
        // Summarizer end at BRIEF_READY when no policies exist; the
        // brief is rendered with all obligations flagged as gaps),
        // then re-process with the broken stub.
        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted {
                val s = regulationRepository.findByIdAndTenantId(regulationId, tenantId)?.status
                assertTrue(s == RegulationStatus.BRIEF_READY || s == RegulationStatus.FAILED)
            }

        workflow.processInBackground(regulationId, tenantId)

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val current = regulationRepository.findByIdAndTenantId(regulationId, tenantId)
                assertEquals(RegulationStatus.FAILED, current!!.status)
                assertNotNull(current.lastError)
                assertTrue(
                    current.lastError!!.contains("non-JSON") || current.lastError!!.contains("Extractor"),
                    "Expected error message about non-JSON output, got: ${current.lastError}",
                )
            }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    @Autowired private lateinit var obligationRepo: io.kalunge.eunomia.domain.ObligationRepository
    @Autowired private lateinit var storageService: io.kalunge.eunomia.storage.StorageService
    @Autowired private lateinit var parser: io.kalunge.eunomia.extraction.DocumentParser
    @Autowired private lateinit var mappingWorkflow: MappingWorkflow

    private fun autowiredObligationRepo() = obligationRepo
    private fun autowiredStorage() = storageService
    private fun autowiredParser() = parser
    private fun autowiredMappingWorkflow() = mappingWorkflow

    private fun registerAndGetToken(email: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"$email","password":"a-12-char-password","displayName":"WF User","tenantSlug":"$slug"}""",
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }

    /**
     * Overrides the default empty-obligations stub from
     * TestcontainersConfiguration for this test class only. Routes
     * extractor vs mapper calls so the chain (Extractor → Mapper)
     * works end-to-end on one bean.
     */
    @TestConfiguration
    class RichStubConfig {
        @Bean
        @Primary
        fun richStubModel(): ChatLanguageModel = RoutingStubChatLanguageModel(
            extractorResponse = """
                {
                  "obligations": [
                    {
                      "summary": "Agents must verify customer identity for transactions above KES 70,000.",
                      "sourceExcerpt": "Agents shall verify identity for any transaction exceeding KES 70,000."
                    },
                    {
                      "summary": "Banks must report suspicious transactions to the FRC within 24 hours.",
                      "sourceExcerpt": "Suspicious transactions must be reported to the FRC within 24 hours."
                    }
                  ]
                }
            """.trimIndent(),
            // With no policies indexed in the test tenant, the Mapper's
            // ANN returns []; this canned response is unused. Kept here
            // so the routing stub is fully configured.
            mapperResponse = """{ "judgments": [] }""",
        )
    }
}
