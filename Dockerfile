# syntax=docker/dockerfile:1.7
#
# Multi-stage build: cache Gradle deps, build the fat jar, then ship a slim
# runtime with only the JRE and the jar. Smaller image, faster cold starts
# on Render's free tier.

# ── Stage 1: build ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and dependency manifests first so Gradle's dep cache
# layer is reusable across source-only changes.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now bring the source in and build the fat jar. Tests run in CI, not
# here — keeping the Docker build fast.
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ── Stage 2: runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Render injects $PORT at runtime. Default 8080 so the container is
# directly runnable in local Docker too.
ENV PORT=8080
EXPOSE 8080

# Non-root user — defence in depth even though there's no privileged
# work happening here.
RUN useradd --system --create-home --shell /usr/sbin/nologin eunomia
USER eunomia

COPY --from=build /workspace/build/libs/*.jar app.jar

# JAVA_OPTS so Render can inject -Xmx etc. without rebuilding.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
