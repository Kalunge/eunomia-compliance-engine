package io.kalunge.eunomia.diff

import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.model.chat.ChatLanguageModel
import io.kalunge.eunomia.RoutingStubChatLanguageModel
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.Regulation
import io.kalunge.eunomia.domain.RegulationRepository
import io.kalunge.eunomia.domain.RegulationStatus
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import org.awaitility.Awaitility.await
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
 * Integration test for POST /api/v1/diff. Spins up the full Spring
 * context with Testcontainers Postgres + pgvector and a routing stub
 * LLM. The stub returns "unchanged → baselineIndex 0" for every diff
 * judge call so the response shape is deterministic regardless of the
 * upload order.
 *
 * Saga timing: with `eunomia.mapper.call-delay-ms=0` in
 * application-test.yml, each upload's full saga (PARSING →
 * BRIEF_READY) completes in ~2-3s. Two uploads run in parallel because
 * each one's workflow runs on its own @Async executor.
 */
@Import(TestcontainersConfiguration::class, RegulationDiffApiTest.DiffStubConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegulationDiffApiTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var regulationRepository: RegulationRepository

    private lateinit var token: String
    private lateinit var tenantId: UUID

    @BeforeEach
    fun seed() {
        val slug = "diff-${System.nanoTime()}"
        val tenant = tenantRepository.save(Tenant(name = "Diff $slug", slug = slug))
        tenantId = tenant.id
        val email = "diff-${System.nanoTime()}@example.com"
        token = registerAndGetToken(email, slug)
    }

    @Test
    fun `diffs two mapped regulations and returns four buckets`() {
        val baselineId = uploadAndWaitDiffable("Baseline.txt", "Some baseline regulatory text.")
        val candidateId = uploadAndWaitDiffable("Candidate.txt", "Some candidate regulatory text.")

        mockMvc.perform(
            post("/api/v1/diff")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baselineId":"$baselineId","candidateId":"$candidateId"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.baselineId").value(baselineId.toString()))
            .andExpect(jsonPath("$.candidateId").value(candidateId.toString()))
            .andExpect(jsonPath("$.unchanged").isArray)
            .andExpect(jsonPath("$.modified").isArray)
            .andExpect(jsonPath("$.added").isArray)
            .andExpect(jsonPath("$.removed").isArray)
            .andExpect(jsonPath("$.summary.totalBaseline").value(2))
            .andExpect(jsonPath("$.summary.totalCandidate").value(2))
            .andExpect(jsonPath("$.summary.materialChangeScore").exists())
    }

    @Test
    fun `returns 400 when baselineId equals candidateId`() {
        val regId = uploadAndWaitDiffable("Same.txt", "Some regulation.")

        mockMvc.perform(
            post("/api/v1/diff")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baselineId":"$regId","candidateId":"$regId"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid_input"))
    }

    @Test
    fun `returns 404 when baselineId does not exist`() {
        val candidateId = uploadAndWaitDiffable("Cand.txt", "Some regulation.")
        val missing = UUID.randomUUID()

        mockMvc.perform(
            post("/api/v1/diff")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baselineId":"$missing","candidateId":"$candidateId"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("regulation_not_found"))
    }

    @Test
    fun `returns 409 when a regulation is in a non-diffable state`() {
        val mappedId = uploadAndWaitDiffable("Mapped.txt", "Some regulation.")

        // Reuse the real user that uploaded the mapped regulation as
        // uploaded_by — the column has a FK to users(id), so a random
        // UUID would trip a constraint violation. Then persist a
        // second regulation directly in EXTRACTED state; the row is
        // valid but the diff rejects it because mapping hasn't run.
        val realUploader = regulationRepository.findByIdAndTenantId(mappedId, tenantId)!!.uploadedBy
        val extractedReg = regulationRepository.save(
            Regulation(
                tenantId = tenantId,
                title = "Extracted-only",
                sourceFilename = "x.txt",
                storageKey = "fake-key",
                mimeType = "text/plain",
                sizeBytes = 0,
                status = RegulationStatus.EXTRACTED,
                uploadedBy = realUploader,
            ),
        )

        mockMvc.perform(
            post("/api/v1/diff")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baselineId":"$mappedId","candidateId":"${extractedReg.id}"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("regulation_not_diffable"))
    }

    @Test
    fun `returns 401 when unauthenticated`() {
        mockMvc.perform(
            post("/api/v1/diff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baselineId":"${UUID.randomUUID()}","candidateId":"${UUID.randomUUID()}"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    private fun uploadAndWaitDiffable(filename: String, text: String): UUID {
        val file = MockMultipartFile("file", filename, "text/plain", text.toByteArray())
        val resp = mockMvc.perform(
            multipart("/api/v1/regulations").file(file).header("Authorization", "Bearer $token"),
        ).andExpect(status().isCreated).andReturn()
        val id = UUID.fromString(objectMapper.readTree(resp.response.contentAsString)["id"].asText())

        await().atMost(Duration.ofSeconds(25)).untilAsserted {
            val r = regulationRepository.findByIdAndTenantId(id, tenantId)
            val s = r?.status
            assert(s in setOf(RegulationStatus.MAPPED, RegulationStatus.BRIEF_GENERATING, RegulationStatus.BRIEF_READY)) {
                "expected diffable state for $id, got $s"
            }
        }

        return id
    }

    private fun registerAndGetToken(email: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"a-12-char-password","displayName":"Diff User","tenantSlug":"$slug"}"""),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }

    @TestConfiguration
    class DiffStubConfig {
        @Bean
        @Primary
        fun diffStub(): ChatLanguageModel = RoutingStubChatLanguageModel(
            extractorResponse = """
                {
                  "obligations": [
                    { "summary": "Verify customer identity above KES 70,000.",
                      "sourceExcerpt": "Verify identity above KES 70,000." },
                    { "summary": "Report suspicious transactions within 24 hours.",
                      "sourceExcerpt": "Report SARs within 24 hours." }
                  ]
                }
            """.trimIndent(),
            mapperResponse = """{ "judgments": [] }""",
            diffResponse = """{ "verdict": "unchanged", "baselineIndex": 0, "rationale": "Same rule, restated." }""",
        )
    }
}
