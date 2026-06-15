package io.kalunge.eunomia.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Runs `flyway.repair()` before `flyway.migrate()` on every boot.
 *
 * Repair reconciles checksum drift between the deployed
 * `flyway_schema_history` table and the on-disk migration files,
 * **without re-running any migration**. It exists for one realistic
 * scenario: a migration file gets edited after it's been applied to
 * a running environment (e.g. removing an outdated seed INSERT, or
 * adjusting whitespace).
 *
 * Concretely, V2 had a seeded placeholder admin user removed during
 * Sprint 1 (commit af1bf4c). Environments that ran the original V2
 * had its checksum recorded in flyway_schema_history; subsequent
 * deploys with the edited V2 fail validation with a
 * `Migration checksum mismatch` error.
 *
 * repair() is idempotent — once history and files agree, it's a
 * no-op. Keeping it in the boot path defends against future churn
 * of the same shape without affecting the steady-state cost.
 *
 * What repair does NOT do: change the actual schema. The DDL that
 * V2 originally executed (CREATE TABLE users / regulations /
 * obligations) is still in effect. We're only updating the
 * checksum that Flyway tracks, not re-running any DDL.
 */
@Configuration
class FlywayConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy = FlywayMigrationStrategy { flyway ->
        log.info("Running Flyway repair() before migrate() to reconcile any checksum drift")
        runCatching { flyway.repair() }
            .onFailure { log.warn("Flyway repair failed (continuing to migrate): {}", it.message) }
        flyway.migrate()
    }
}
