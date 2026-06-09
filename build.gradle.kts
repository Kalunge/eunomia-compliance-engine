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
	implementation("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
	implementation("dev.langchain4j:langchain4j-spring-boot-starter:${property("langchain4jVersion")}")
	implementation("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}") // Groq is OpenAI-compatible
	implementation("dev.langchain4j:langchain4j-cohere:${property("langchain4jVersion")}")
	implementation("dev.langchain4j:langchain4j-pgvector:${property("langchain4jVersion")}")

	// ── Document parsing ─────────────────────────────────────────────
	implementation("org.apache.tika:tika-core:2.9.2")
	implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")

	// ── API docs ─────────────────────────────────────────────────────
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

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

tasks.withType<Test> {
	useJUnitPlatform()
	// docker-java negotiates API v1.32 by default, which OrbStack (Docker 25+)
	// rejects. Force a modern version so Testcontainers works on local machines
	// with newer Docker engines.
	systemProperty("api.version", "1.43")
	environment("DOCKER_API_VERSION", "1.43")
}
