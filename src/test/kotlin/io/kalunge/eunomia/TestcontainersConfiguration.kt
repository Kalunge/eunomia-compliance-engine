package io.kalunge.eunomia

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.output.Response
import io.kalunge.eunomia.embedding.EmbeddingService
import io.kalunge.eunomia.embedding.StubEmbeddingService
import io.kalunge.eunomia.extraction.StubChatLanguageModel
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /**
     * Uses the pgvector image rather than vanilla Postgres so Flyway migrations
     * that depend on `CREATE EXTENSION vector` succeed in tests.
     *
     * `asCompatibleSubstituteFor("postgres")` is required because the image name
     * doesn't include "postgres" — Testcontainers' PostgreSQLContainer rejects
     * unrecognised images otherwise.
     */
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> {
        return PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"),
        )
    }

    /**
     * Replaces the production ChatLanguageModel for any test that loads
     * the Spring context. Production [io.kalunge.eunomia.config.LangChain4jConfig]
     * is annotated @Profile("!test") so it's silent here, and this bean
     * takes its place — no real Groq API calls from CI, no quota burn.
     */
    @Bean
    fun chatLanguageModel(): ChatLanguageModel = StubChatLanguageModel(
        """{ "obligations": [] }""",
    )

    /**
     * Replaces the production CohereEmbeddingService for any test that
     * loads the Spring context. The production [io.kalunge.eunomia.config.CohereConfig]
     * is annotated @Profile("!test") so it's silent here, and the
     * StubEmbeddingService produces deterministic 1024-d L2-normalised
     * vectors — no Cohere quota burn from CI.
     */
    @Bean
    fun embeddingService(): EmbeddingService = StubEmbeddingService()

    /**
     * Replaces the production StreamingChatLanguageModel for any test
     * that loads the Spring context. Production LangChain4jConfig is
     * @Profile("!test") so it's silent here; this bean satisfies any
     * downstream wiring (e.g. HelpService) without making a real Groq
     * call. The stub emits one chunk then completes.
     */
    @Bean
    fun streamingChatLanguageModel(): StreamingChatLanguageModel =
        object : StreamingChatLanguageModel {
            override fun generate(
                messages: MutableList<ChatMessage>,
                handler: StreamingResponseHandler<AiMessage>,
            ) {
                val text = "Eunomia help (test stub)."
                handler.onNext(text)
                handler.onComplete(Response.from(AiMessage.from(text)))
            }
        }
}
