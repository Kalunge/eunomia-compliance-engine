plugins {
	kotlin("jvm") version "2.0.21"
	kotlin("plugin.spring") version "2.0.21"
	kotlin("plugin.jpa") version "2.0.21"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.kalunge"
version = "0.0.1-SNAPSHOT"
description = "Eunomia — AI-powered banking compliance engine"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["langchain4jVersion"] = "0.36.2"

// Spring Boot 3.5's transitive docker-java is too old for modern Docker
// engines (Docker 25+, OrbStack) which require API v1.40+. Pinning a
// newer docker-java unblocks Testcontainers in those environments.
configurations.all {
	resolutionStrategy.eachDependency {
		if (requested.group == "com.github.docker-java") {
			useVersion("3.5.3")
		}
	}
}

dependencies {
	// ── Spring Boot ──────────────────────────────────────────────────
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")

	// ── Persistence ──────────────────────────────────────────────────
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// ── Kotlin ───────────────────────────────────────────────────────
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")

	// ── Auth (JWT) ───────────────────────────────────────────────────
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// ── AI integration ───────────────────────────────────────────────
	// Note: deliberately NOT using langchain4j-spring-boot-starter.
	// It auto-configures a ContentRetriever that demands a single
	// EmbeddingModel bean, which clashes with our two Cohere beans
	// (one tuned for "search_document", one for "search_query"). We
	// hand-roll all agent + RAG wiring through @Configuration classes
	// in io.kalunge.eunomia.config, so the starter's autoconfig isn't
	// needed.
	implementation("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
	implementation("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}") // Groq is OpenAI-compatible
	implementation("dev.langchain4j:langchain4j-cohere:${property("langchain4jVersion")}")
	implementation("dev.langchain4j:langchain4j-pgvector:${property("langchain4jVersion")}")

	// ── Document parsing ─────────────────────────────────────────────
	implementation("org.apache.tika:tika-core:2.9.2")
	implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")

	// ── Brief rendering (Markdown → HTML → PDF) ──────────────────────
	// commonmark-java is the reference CommonMark parser maintained by
	// Atlassian; openhtmltopdf turns the rendered HTML into a PDF/A
	// without native deps. Used by BriefPdfRenderer in Sprint 3.
	implementation("org.commonmark:commonmark:0.22.0")
	implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")

	// ── Tokenisation (for policy chunking) ───────────────────────────
	// JTokkit implements OpenAI's BPE tokenisers (cl100k_base et al).
	// Cohere uses a slightly different tokeniser, but the deviation is
	// small enough that cl100k_base is a fine approximation for chunk
	// boundary calculations.
	implementation("com.knuddels:jtokkit:1.1.0")

	// ── API docs ─────────────────────────────────────────────────────
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

	// ── Local-dev convenience ────────────────────────────────────────
	// Lets Spring Boot read a .env file at the project root so dev
	// secrets (JWT_SECRET, etc.) stay out of source control. In
	// production (Render injects env vars directly) the absence of
	// .env is a silent no-op.
	implementation("me.paulschwarz:spring-dotenv:4.0.0")

	// ── Test ─────────────────────────────────────────────────────────
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("io.mockk:mockk:1.13.13")
	testImplementation("com.ninja-squad:springmockk:4.0.2")
	testImplementation("org.assertj:assertj-core")
	testImplementation("io.rest-assured:rest-assured:5.5.0")
	testImplementation("org.wiremock:wiremock-standalone:3.9.2")
	testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	// docker-java negotiates API v1.32 by default, which OrbStack (Docker 25+)
	// rejects. Force a modern version so Testcontainers works on local machines
	// with newer Docker engines.
	systemProperty("api.version", "1.43")
	environment("DOCKER_API_VERSION", "1.43")
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("ai-eval")
	}
}

val aiEvalReportDir = layout.buildDirectory.dir("reports/ai-eval")

tasks.register<Test>("aiEval") {
	group = "verification"
	description = "Runs AI evaluation cases separately from the deterministic test suite."

	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath

	useJUnitPlatform {
		includeTags("ai-eval")
	}

	systemProperty("aiEval.reportDir", aiEvalReportDir.get().asFile.absolutePath)
	outputs.dir(aiEvalReportDir)

	doFirst {
		delete(aiEvalReportDir)
	}
}
