<div align="center">

# Eunomia

**AI-powered banking compliance engine.**

Upload a regulatory circular. Get back an executive impact brief, a
gap analysis against your internal policies, and an obligation-level
diff if there's an amendment to compare against. All in about ninety
seconds.

[![CI](https://github.com/Kalunge/eunomia-compliance-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/Kalunge/eunomia-compliance-engine/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev)
[![AWS](https://img.shields.io/badge/AWS-EC2%20%2B%20RDS-FF9900?logo=amazonaws&logoColor=white)](https://aws.amazon.com)
[![Vercel](https://img.shields.io/badge/Vercel-deployed-000000?logo=vercel&logoColor=white)](https://vercel.com)

### **[➜ Live demo](https://eunomia-web.vercel.app)** &nbsp;·&nbsp; **[Health check](https://eunomia-web.vercel.app/actuator/health)** &nbsp;·&nbsp; **[Trello board](https://trello.com/b/43NaWowH/eunomia-compliance-engine)**

</div>

---

> Eunomia is the Greek goddess of good order and lawful conduct, daughter
> of Themis. The name describes the state the product produces: a bank
> in good order, lawfully aligned with its regulators.

## What it does

When a regulator publishes a circular, a bank's compliance team has to
pull out every obligation, decide which internal policies cover it,
figure out which products are affected, and brief leadership. For a
typical CBK circular that's most of a senior officer's day. Every
amendment means doing it again, plus spotting what changed.

Eunomia automates that loop end to end:

- **Extract** every obligation from a regulatory PDF, DOCX, or text
  document
- **Map** each obligation against your internal policy catalogue
  using vector search and an LLM judge
- **Surface gaps** where no internal policy satisfies a regulator's
  rule, with confidence scores
- **Tag affected products and services** so risk teams know what's
  exposed
- **Compose an executive impact brief** in Markdown and PDF
- **Diff two regulations** at the obligation level — paraphrase-
  tolerant, with one-sentence rationales on every changed pair
- **Stream live agent progress** to the UI via SSE so compliance
  teams can see the system working in real time
- **Ask the in-app AI assistant** anything about the platform via
  token-by-token streaming

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│  Browser — React 18 + Vite + Tailwind + TanStack Query         │
└──────────────────────────────┬─────────────────────────────────┘
                               │
┌──────────────────────────────▼─────────────────────────────────┐
│  Vercel edge — static SPA + /api/* rewrite to EC2              │
└──────────────────────────────┬─────────────────────────────────┘
                               │
┌──────────────────────────────▼─────────────────────────────────┐
│  Spring Boot 3.5 + Kotlin 2.0 + Java 21 on AWS EC2 (us-east-1) │
│                                                                │
│  JWT auth → TenantContext                                      │
│  Multi-agent saga (one @Async stage each):                     │
│     Extractor → Mapper → ImpactAnalyzer → Summarizer           │
│  Diff agent runs out-of-band on demand                         │
│  SSE for live progress; Spring Data JPA + Flyway               │
└───────────────────┬────────────┬───────────────────────────────┘
                    │            │
                    ▼            ▼
            Postgres 16        Cohere (embeds)
            + pgvector         Groq GPT OSS 120B (LLM)
            on AWS RDS
```

One Spring Boot process behind one EC2 instance, one Postgres on RDS,
one embedding provider, one completion provider. The deeper
architectural rationale, named patterns (saga, two-stage retrieval,
event sourcing, CQRS-lite, strategy, multi-tenant context), and
testing strategy are in **[`design_and_testing.md`](./design_and_testing.md)**.

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | React 18, Vite, TypeScript, Tailwind, TanStack Query, Zustand |
| Backend | Spring Boot 3.5, Kotlin 2.0, Java 21, Spring Security, Spring Data JPA |
| Database | Postgres 16 with the `pgvector` extension |
| LLM (completions) | Groq's GPT OSS 120B via OpenAI-compatible REST |
| LLM (embeddings) | Cohere `embed-english-v3.0`, 1024-d |
| Document parsing | Apache Tika (PDF, DOCX, text) |
| LLM SDK | LangChain4j |
| Auth | JWT (HS256, 256-bit secret) |
| PDF render | openhtmltopdf (pure Java) |
| Frontend host | Vercel (free tier, static + rewrites) |
| Backend host | AWS EC2 t3.micro in us-east-1 |
| Database host | AWS RDS Postgres |
| CI/CD | GitHub Actions + AWS OIDC federation + AWS SSM Run Command |

## Local development

**Prerequisites**: JDK 21, Docker + Docker Compose, Node 20.

```bash
# Terminal 1 — Postgres + pgvector on host port 5434
docker compose up -d postgres

# Terminal 2 — Spring Boot backend on :8080
./gradlew bootRun

# Terminal 3 — Vite dev server on :5173, proxies /api to :8080
cd web && npm install && npm run dev
```

URLs once running:

- Frontend: `http://localhost:5173`
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/docs`
- Health: `http://localhost:8080/actuator/health`

**Run the tests** (Testcontainers boots a real Postgres + pgvector):

```bash
./gradlew test
```

## Configuration

Copy `.env.example` to `.env` and fill the values:

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL for Postgres + pgvector |
| `DATABASE_USERNAME` | DB username |
| `DATABASE_PASSWORD` | DB password |
| `GROQ_API_KEY` | LLM completions |
| `COHERE_API_KEY` | Embeddings |
| `JWT_SECRET` | HS256 signing secret, ≥ 256 bits |
| `STORAGE_ROOT` | Where uploaded blobs land (defaults to `./data`) |
| `MAPPER_CALL_DELAY_MS` | Throttle between Mapper LLM calls (free-tier TPM) |

## Project structure

```
src/main/kotlin/io/kalunge/eunomia/
  ├── auth/         # JWT, Spring Security, registration & login
  ├── regulation/   # Upload, list, detail, brief PDF
  ├── extraction/   # Extractor agent + JSON parsing
  ├── mapping/      # Mapper agent + pgvector ANN search
  ├── impact/       # Impact analyser + product tagging
  ├── summarize/    # Brief composition + PDF render
  ├── diff/         # Obligation-level regulation diff
  ├── help/         # Streaming in-app AI assistant
  ├── dashboard/    # Tenant KPIs (read-model projection)
  ├── domain/       # JPA entities + repositories
  ├── tenancy/      # Multi-tenant context + filter
  ├── workflow/     # Saga orchestrator + SSE events
  ├── embedding/    # Cohere strategy + chunking
  ├── policy/       # Internal policy upload + indexing
  ├── storage/      # Pluggable blob storage
  ├── events/       # SSE controller + workflow event service
  └── config/       # Spring beans (security, async, LangChain4j)

src/main/resources/
  ├── application.yml
  └── db/migration/   # Flyway migrations

web/src/
  ├── pages/         # Each route's top-level component
  ├── components/    # AppLayout, ProtectedRoute, etc.
  ├── lib/           # API clients (regulations, policies, diff, help)
  └── stores/        # Zustand auth store
```

## Deployment

The backend runs on AWS EC2 in `us-east-1`. The frontend runs on
Vercel and proxies `/api/*` to the EC2 over HTTPS. Continuous
deployment is wired through GitHub Actions:

1. Every push runs the test job against a Testcontainers Postgres
2. Pushes to `main` build the boot JAR
3. The build job assumes an IAM role via GitHub OIDC federation
   (no long-lived AWS keys live in GitHub)
4. The JAR is uploaded to S3 under two keys (`app-<sha>.jar` and
   `latest.jar`)
5. An SSM Run Command instructs the EC2 to swap the JAR, restart
   the `eunomia` systemd unit, and poll `/actuator/health` for up
   to 120 seconds
6. On health-check failure, the script auto-rolls-back to the
   previous JAR

Vercel auto-deploys the frontend on every push to `main`.

Full deployment notes, including the IAM trust policy and the SSM
deploy script, are in
[`design_and_testing.md`](./design_and_testing.md).

## Documentation

- **[`design_and_testing.md`](./design_and_testing.md)** — design
  decisions, patterns and rationale, multi-vendor LLM split,
  deployment topology, CI/CD, testing layers, failure modes
- **In-app Help & Docs** — open the deployed app, sign in, click
  **Help & Docs** in the sidebar for 13 topic guides plus an AI
  assistant that answers free-form questions about the platform
- **Swagger UI** — `http://localhost:8080/docs` when running locally

## Capstone context

Quantic School of Business and Technology — MS in Software Engineering
capstone project. Solo project (Product Owner, Scrum Master, and
sole developer). Three agile sprints; the Trello board linked above
tracks the full backlog. Sprint demos are recorded; the final
demonstration video is submitted alongside this repository.

## License

For academic submission. Not licensed for external commercial use.
