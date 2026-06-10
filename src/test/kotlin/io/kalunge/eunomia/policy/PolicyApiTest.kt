package io.kalunge.eunomia.policy

import com.fasterxml.jackson.databind.ObjectMapper
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.PolicyRepository
import io.kalunge.eunomia.domain.PolicyStatus
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import org.awaitility.Awaitility.await
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyApiTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var policyRepository: PolicyRepository

    private lateinit var primaryToken: String
    private lateinit var rivalToken: String
    private lateinit var primaryTenantId: UUID

    @BeforeEach
    fun seed() {
        val primarySlug = "primary-${unique()}"
        val rivalSlug = "rival-${unique()}"
        val primary = tenantRepository.save(Tenant(name = "Primary $primarySlug", slug = primarySlug))
        tenantRepository.save(Tenant(name = "Rival $rivalSlug", slug = rivalSlug))
        primaryTenantId = primary.id
        primaryToken = registerAndGetToken("primary-${unique()}@example.com", primarySlug)
        rivalToken = registerAndGetToken("rival-${unique()}@example.com", rivalSlug)
    }

    @Test
    fun `upload list detail and delete a policy`() {
        val file = MockMultipartFile(
            "file",
            "policy.txt",
            "text/plain",
            "Agents must verify customer identity for cash deposits above KES 70,000.".toByteArray(),
        )

        val uploadResp = mockMvc.perform(
            multipart("/api/v1/policies")
                .file(file)
                .header("Authorization", "Bearer $primaryToken"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PARSING"))
            .andReturn()

        val policyId = UUID.fromString(
            objectMapper.readTree(uploadResp.response.contentAsString)["id"].asText(),
        )

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val p = policyRepository.findByIdAndTenantId(policyId, primaryTenantId)
            assert(p?.status == PolicyStatus.INDEXED) { "Expected INDEXED, got ${p?.status}" }
        }

        mockMvc.perform(
            get("/api/v1/policies").header("Authorization", "Bearer $primaryToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(policyId.toString()))
            .andExpect(jsonPath("$.items[0].chunkCount").isNumber)

        mockMvc.perform(
            get("/api/v1/policies/$policyId").header("Authorization", "Bearer $primaryToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(policyId.toString()))
            .andExpect(jsonPath("$.status").value("INDEXED"))
            .andExpect(jsonPath("$.chunks").isArray)

        mockMvc.perform(
            delete("/api/v1/policies/$policyId").header("Authorization", "Bearer $primaryToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/policies/$policyId").header("Authorization", "Bearer $primaryToken"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `another tenant cannot see this tenant's policy`() {
        val file = MockMultipartFile(
            "file",
            "secret.txt",
            "text/plain",
            "Internal policy text.".toByteArray(),
        )
        val uploadResp = mockMvc.perform(
            multipart("/api/v1/policies")
                .file(file)
                .header("Authorization", "Bearer $primaryToken"),
        ).andExpect(status().isCreated).andReturn()
        val policyId = UUID.fromString(
            objectMapper.readTree(uploadResp.response.contentAsString)["id"].asText(),
        )

        mockMvc.perform(
            get("/api/v1/policies/$policyId").header("Authorization", "Bearer $rivalToken"),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get("/api/v1/policies").header("Authorization", "Bearer $rivalToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))

        mockMvc.perform(
            delete("/api/v1/policies/$policyId").header("Authorization", "Bearer $rivalToken"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `upload without auth is rejected with 401`() {
        val file = MockMultipartFile("file", "anon.txt", "text/plain", "x".toByteArray())
        mockMvc.perform(multipart("/api/v1/policies").file(file))
            .andExpect(status().isUnauthorized)
    }

    private fun registerAndGetToken(email: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"$email","password":"a-12-char-password","displayName":"User","tenantSlug":"$slug"}""",
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }

    private fun unique(): String = System.nanoTime().toString()
}
