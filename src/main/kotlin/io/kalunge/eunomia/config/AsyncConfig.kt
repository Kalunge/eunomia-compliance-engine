package io.kalunge.eunomia.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Enables Spring's @Async — the Extractor workflow runs off the request
 * thread so the upload endpoint can return immediately. Default
 * ThreadPoolTaskExecutor is fine for a single LLM call per upload; if
 * we ever scale to dozens of concurrent extractions a bespoke executor
 * with bounded queue size would go here.
 */
@Configuration
@EnableAsync
class AsyncConfig
