package io.kalunge.eunomia

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
}
