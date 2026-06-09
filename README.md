# Eunomia

**AI-powered banking compliance engine.** Upload a regulatory document
(a CBK circular, a Basel addendum, a prudential directive) and Eunomia
extracts the obligations, maps them against your bank's internal policy
catalog via RAG, flags gaps, identifies affected products and services,
and produces a structured impact brief for leadership.

*Eunomia — Greek goddess of good order and lawful conduct, daughter of
Themis. The name describes the state the product produces: a bank in good
order, lawfully aligned with its regulators.*

## Status

Capstone project for the Quantic MSc in Software Engineering — actively
under development. This repo follows a three-sprint agile cadence with a
live Trello board, recorded sprint demos, and CI/CD-gated deploys to
Render.

- Live deployment: *(coming end of Sprint 0)*
- Task board: *(coming end of Sprint 0)*

## Architecture summary

A Kotlin + Spring Boot 3 backend that orchestrates a multi-agent workflow
(Extractor → Mapper → ImpactAnalyzer → Summarizer) over uploaded
regulatory documents. A Vue 3 dashboard provides authentication, the
internal policy catalog, regulation ingestion, real-time agent
telemetry, and an overview dashboard.

- **Backend**: Kotlin 2 + Spring Boot 3 + Spring Security (JWT)
- **Persistence**: PostgreSQL with the `pgvector` extension (one
  datastore for relational state + embeddings)
- **AI integration**: LangChain4j with strategy-pattern provider
  selection — Groq Llama 3.3 70B for generation, Cohere
  `embed-english-v3.0` for embeddings
- **Frontend**: Vue 3 + Vite + TypeScript + Tailwind
- **Deployment**: Single Docker container on Render, CI/CD via GitHub
  Actions

Detailed architecture, named patterns (saga, bulkhead, event sourcing,
CQRS-lite, strategy, multi-tenant context), and testing strategy live in
the `design-and-testing.md` document (added at the end of Sprint 3).

## Local development

**Prerequisites**

- JDK 21 (Corretto, Temurin, or any standard distribution)
- Docker + Docker Compose
- Node 20 (for the frontend, added Sprint 1)

**Start the stack locally**

```bash
# 1. Bring up Postgres + pgvector (host port 5434 to avoid clashing with
#    native Postgres or other projects on 5432/5433).
docker compose up -d postgres

# 2. Run the app.
./gradlew bootRun
```

The app listens on `http://localhost:8080`. Swagger UI is at `/docs`,
health at `/actuator/health`.

**Run the tests**

```bash
./gradlew test
```

Testcontainers spins up a real Postgres + pgvector instance for the
integration tests.

## Configuration

Environment variables (defaults in `application.yml`):

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL for Postgres + pgvector |
| `DATABASE_USERNAME` | DB username |
| `DATABASE_PASSWORD` | DB password |
| `PORT` | HTTP port (Render injects this) |
| `GROQ_API_KEY` | Groq Llama 3.3 generation (Sprint 1+) |
| `COHERE_API_KEY` | Cohere embeddings (Sprint 1+) |
| `JWT_SECRET` | HS256 signing secret for auth tokens (Sprint 1+) |

Copy `.env.example` to `.env` and fill in your values before running.

## Project structure

```
src/main/kotlin/io/kalunge/eunomia/
  ├── api/         # REST controllers
  ├── agents/      # Extractor, Mapper, ImpactAnalyzer, Summarizer
  ├── config/      # Spring config (security, async, OpenAPI)
  ├── domain/      # Entities + repositories
  └── workflow/    # Saga orchestrator and progress streaming

src/main/resources/
  ├── application.yml
  └── db/migration/  # Flyway migrations
```

## Process

Three sprints over ~10 weeks. Each sprint ends with a recorded software
demonstration. The board (linked above when ready) tracks the full
backlog. Acting solo as Product Owner, Scrum Master, and developer for
this capstone.

## License

For academic submission. Not licensed for external use.
