package io.kalunge.eunomia.evaluation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kalunge.eunomia.mapping.PolicyChunkSearch
import io.kalunge.eunomia.policy.PolicyChunkRow
import io.kalunge.eunomia.policy.PolicyChunkWriter
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import kotlin.system.measureTimeMillis

@Tag("ai-eval")
class RetrievalEvaluationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `evaluates Cohere retrieval through pgvector`() {
        val evaluationModel = CohereEvaluationEmbeddingFactory.createFromEnvironment()
        assumeTrue(
            evaluationModel != null,
            "Set COHERE_API_KEY to run the live retrieval evaluation.",
        )
        val model = requireNotNull(evaluationModel)
        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable,
            "Docker is required to run the live pgvector retrieval evaluation.",
        )
        val dataset = EvaluationDatasetLoader(objectMapper)
            .load("ai-eval/retrieval-v1.json")
        val corpus = readCorpus("ai-eval/retrieval-corpus-v1.json")

        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"),
        )
        postgres.start()

        try {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            val dataSource = DriverManagerDataSource(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
            val jdbcTemplate = JdbcTemplate(dataSource)
            val chunkWriter = PolicyChunkWriter(jdbcTemplate)
            val chunkSearch = PolicyChunkSearch(jdbcTemplate)
            val primaryTenantId = stableId("retrieval-primary-tenant")
            val otherTenantId = stableId("retrieval-other-tenant")
            val primaryPolicyId = stableId("retrieval-primary-policy")
            val otherPolicyId = stableId("retrieval-other-policy")

            seedTenantAndPolicy(jdbcTemplate, primaryTenantId, primaryPolicyId, "retrieval-primary")
            seedTenantAndPolicy(jdbcTemplate, otherTenantId, otherPolicyId, "retrieval-other")

            val embeddings = model.embeddingService.embedDocuments(corpus.chunks.map { it.text })
            require(embeddings.size == corpus.chunks.size) {
                "Cohere returned ${embeddings.size} embeddings for ${corpus.chunks.size} chunks."
            }

            val chunkIds = corpus.chunks.associate { chunk ->
                chunk.id to stableId("retrieval-chunk:${chunk.id}")
            }
            val logicalIdsByUuid = chunkIds.entries.associate { (logicalId, uuid) -> uuid to logicalId }
            chunkWriter.insertAll(
                corpus.chunks.zip(embeddings).mapIndexed { index, (chunk, embedding) ->
                    val otherTenant = chunk.tenant == "other"
                    PolicyChunkRow(
                        id = chunkIds.getValue(chunk.id),
                        tenantId = if (otherTenant) otherTenantId else primaryTenantId,
                        policyId = if (otherTenant) otherPolicyId else primaryPolicyId,
                        chunkIndex = index,
                        text = chunk.text,
                        pageStart = null,
                        pageEnd = null,
                        embedding = embedding,
                    )
                },
            )

            val forbiddenIds = corpus.chunks
                .filter { it.tenant == "other" }
                .map { it.id }
                .toSet()
            val results = dataset.cases.map { case ->
                val input = objectMapper.treeToValue(
                    case.input,
                    RetrievalEvaluationInput::class.java,
                )
                val expected = objectMapper.treeToValue(
                    case.expected,
                    ExpectedRetrievalOutput::class.java,
                )

                var result: EvaluationCaseResult? = null
                val durationMs = measureTimeMillis {
                    result = runCatching {
                        val queryVector = model.embeddingService.embedQuery(input.query)
                        val rankedIds = chunkSearch.findNearest(
                            tenantId = primaryTenantId,
                            queryVector = queryVector,
                            k = 3,
                        ).mapNotNull { logicalIdsByUuid[it.chunkId] }
                        val score = RetrievalEvaluationMetrics.score(
                            rankedChunkIds = rankedIds,
                            relevantChunkIds = expected.relevantChunkIds,
                            forbiddenChunkIds = forbiddenIds,
                        )
                        val passed = score.recallAt3 == 1.0 && score.tenantIsolation == 1.0
                        EvaluationCaseResult(
                            caseId = case.id,
                            status = if (passed) EvaluationStatus.PASS else EvaluationStatus.FAIL,
                            durationMs = 0,
                            metrics = score.asMetrics(),
                            details = "Expected ${expected.relevantChunkIds.sorted()}; top3=$rankedIds.",
                        )
                    }.getOrElse { error ->
                        EvaluationCaseResult(
                            caseId = case.id,
                            status = EvaluationStatus.ERROR,
                            durationMs = 0,
                            metrics = mapOf(
                                "recallAt1" to 0.0,
                                "recallAt3" to 0.0,
                                "reciprocalRank" to 0.0,
                                "resultCompleteness" to 0.0,
                                "tenantIsolation" to 0.0,
                            ),
                            details = "${error::class.simpleName}: ${error.message}",
                        )
                    }
                }
                requireNotNull(result).copy(durationMs = durationMs)
            }

            val report = EvaluationReportFactory.create(
                dataset = dataset,
                model = model.modelName,
                results = results,
            )
            val reportRoot = Path.of(
                System.getProperty("aiEval.reportDir", "build/reports/ai-eval"),
            )
            EvaluationReportWriter(objectMapper)
                .write(report, reportRoot.resolve(dataset.suiteId))
        } finally {
            postgres.stop()
        }
    }

    private fun readCorpus(resourcePath: String): RetrievalCorpus {
        val stream = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Retrieval corpus not found: $resourcePath")
        return stream.use { objectMapper.readValue(it, RetrievalCorpus::class.java) }
            .also {
                require(it.schemaVersion == 1) {
                    "Unsupported retrieval corpus schema version ${it.schemaVersion}."
                }
                require(it.chunks.map { chunk -> chunk.id }.toSet().size == it.chunks.size) {
                    "Retrieval corpus contains duplicate chunk IDs."
                }
            }
    }

    private fun seedTenantAndPolicy(
        jdbcTemplate: JdbcTemplate,
        tenantId: UUID,
        policyId: UUID,
        slug: String,
    ) {
        val userId = stableId("$slug-user")
        jdbcTemplate.update(
            """
            INSERT INTO tenants (id, name, slug)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            tenantId,
            "Retrieval Evaluation",
            slug,
        )
        jdbcTemplate.update(
            """
            INSERT INTO users (id, tenant_id, email, password_hash, display_name, role)
            VALUES (?, ?, ?, ?, ?, 'ADMIN')
            """.trimIndent(),
            userId,
            tenantId,
            "$slug@example.com",
            "not-used",
            "Evaluation User",
        )
        jdbcTemplate.update(
            """
            INSERT INTO policies
                (id, tenant_id, title, source_filename, storage_key, mime_type,
                 size_bytes, status, uploaded_by)
            VALUES (?, ?, ?, ?, ?, 'text/plain', 1, 'INDEXED', ?)
            """.trimIndent(),
            policyId,
            tenantId,
            "Retrieval Evaluation Policy",
            "$slug.txt",
            "evaluation/$slug.txt",
            userId,
        )
    }

    private fun stableId(value: String): UUID =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
