package io.kalunge.eunomia.auth

import com.fasterxml.jackson.databind.ObjectMapper
import io.kalunge.eunomia.TestcontainersConfiguration
import io.kalunge.eunomia.domain.Tenant
import io.kalunge.eunomia.domain.TenantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * End-to-end happy + sad paths for the auth API. Uses a real Postgres
 * (Testcontainers) and the real Spring Security filter chain — no mocks.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenantRepository: TenantRepository

    private lateinit var primaryTenant: Tenant
    private lateinit var secondTenant: Tenant

    @BeforeEach
    fun seedTenants() {
        // V1 seeds 'demo-bank'; we just look it up here and create a
        // second tenant so the cross-tenant isolation test has somewhere
        // to look. Idempotent in case of test re-runs.
        primaryTenant = tenantRepository.findBySlug("demo-bank")!!
        secondTenant = tenantRepository.findBySlug("rival-bank")
            ?: tenantRepository.save(Tenant(name = "Rival Bank", slug = "rival-bank"))
    }

    @Test
    fun `register issues a token and returns the new user`() {
        val response = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "alice-${unique()}@example.com",
                      "password": "correct-horse-battery-staple",
                      "displayName": "Alice",
                      "tenantSlug": "demo-bank"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.user.email").exists())
            .andExpect(jsonPath("$.user.tenantId").value(primaryTenant.id.toString()))
            .andReturn()

        // The returned token should be a non-empty JWT (three base64 segments).
        val body = objectMapper.readTree(response.response.contentAsString)
        val token = body["token"].asText()
        assertEquals(3, token.split(".").size, "JWT should have three segments")
    }

    @Test
    fun `login fails with the wrong password`() {
        val email = "bob-${unique()}@example.com"
        register(email, "right-password-very-long", "demo-bank")

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "$email",
                      "password": "wrong-password-also-long",
                      "tenantSlug": "demo-bank"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("invalid_credentials"))
    }

    @Test
    fun `login fails when the tenant slug is unknown`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "anyone@example.com",
                      "password": "any-password-very-long-here",
                      "tenantSlug": "no-such-bank"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("tenant_not_found"))
    }

    @Test
    fun `protected endpoint rejects requests without a token`() {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me returns the authenticated user when given a valid token`() {
        val email = "carol-${unique()}@example.com"
        val token = register(email, "first-time-user-pw-1", "demo-bank")

        mockMvc.perform(
            get("/api/v1/auth/me").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.tenantId").value(primaryTenant.id.toString()))
    }

    @Test
    fun `same email can register under two different tenants`() {
        val sharedEmail = "shared-${unique()}@example.com"
        val tokenA = register(sharedEmail, "very-long-pw-tenant-a", "demo-bank")
        val tokenB = register(sharedEmail, "very-long-pw-tenant-b", "rival-bank")

        assertNotNull(tokenA)
        assertNotNull(tokenB)
        // Tenant IDs in the responses should differ.
        val userA = meTenantId(tokenA)
        val userB = meTenantId(tokenB)
        assertEquals(primaryTenant.id.toString(), userA)
        assertEquals(secondTenant.id.toString(), userB)
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun register(email: String, password: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"$email","password":"$password","displayName":"User","tenantSlug":"$slug"}""",
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["token"].asText()
    }

    private fun meTenantId(token: String): String {
        val res = mockMvc.perform(
            get("/api/v1/auth/me").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk).andReturn()
        return objectMapper.readTree(res.response.contentAsString)["tenantId"].asText()
    }

    private fun unique(): String = System.nanoTime().toString()
}
