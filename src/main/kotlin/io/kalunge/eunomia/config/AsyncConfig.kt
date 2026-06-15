package io.kalunge.eunomia.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Enables Spring's @Async — the Extractor workflow runs off the request
 * thread so the upload endpoint can return immediately. Default
 * ThreadPoolTaskExecutor is fine for a single LLM call per upload; if
 * we ever scale to dozens of concurrent extractions a bespoke executor
 * with bounded queue size would go here.
 *
 * Also enables @Scheduled so TenantMetricsScheduler fires every 15
 * minutes to self-heal any drift in the tenant_metrics denormalised
 * counts.
 */
@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig
