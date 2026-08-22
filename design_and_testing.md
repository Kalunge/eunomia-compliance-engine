# Eunomia — Design and Testing

The design and testing write-up for this repo. Written the way I'd
explain it to a new engineer joining the team.

## What Eunomia is

When a regulator publishes a circular, a bank's compliance team has
to pull out every obligation, decide which internal policies cover
it, figure out which products are affected, and brief leadership.
For a typical CBK circular that's most of a senior officer's day,
and every amendment means doing it again — plus spotting what
changed.

Eunomia automates the whole loop. An LLM pipeline does the
extraction, mapping, impact tagging, and summarising. A separate
diff feature compares two regulations at the obligation level
(not the text level — regulators reorder paragraphs all the time
without changing substance).

Live: <https://eunomia-web.vercel.app>.

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

One Spring Boot process behind one EC2, one Postgres. No broker, no
Redis, no Temporal. Anything I'd add at this scale would cost more
in operational weight than it'd buy me.

## Tech choices that mattered

**Kotlin on Spring Boot 3.5**, because Kotlin gives me null-safety
and data classes without giving up the Spring ecosystem. Java 21
runtime; one fat JAR on a 1 GB t3.micro is the only realistic shape.

**Postgres 16 with pgvector** so relational data and vector search
live in the same engine. No separate Pinecone or Qdrant to operate,
back up, or pay for.

**Apache Tika** for parsing. One library covers PDF, DOCX, and
plain text — compliance teams send mixed formats.

**LangChain4j** as the LLM SDK. Groq's GPT OSS 120B handles
completions through the OpenAI-compatible endpoint, while Cohere
`embed-english-v3.0` handles embeddings. I originally used Llama
3.3 70B Versatile and migrated before Groq retired that model.

**JWT HS256, 256-bit secret enforced at startup.** I learnt that
length requirement the hard way — my first deploy ran with a
16-char placeholder and the app refused to issue tokens.

**AWS for backend** (EC2 + RDS + S3 + SSM) **and Vercel for the
frontend.** Vercel's rewrites proxy `/api/*` to the EC2 so the
browser sees same-origin — no CORS. Originally I wanted CloudFront
in front of everything, but a new-account verification gate is
still pending, and Vercel turned out to do the job fine.

## Patterns I used

The workflow has to survive partial failure without restarting work
that already succeeded. I modelled it as four sequential, idempotent
stages: Extractor, Mapper, ImpactAnalyzer, and Summarizer. Each stage
runs on its own `@Async` executor and persists its output before
advancing the regulation status, so retrying resumes from the last
valid state. At this scale, that gave me the useful part of a
**saga** without introducing a separate workflow platform.

Retrieval is deliberately split into two stages. The system embeds
an obligation, uses pgvector cosine distance to find candidate
policy chunks, then asks the LLM to judge the top three using a
strict-JSON response with a decision and confidence score. Only
results above the configured threshold (0.6 by default) are
persisted. I reused this structure for obligation-to-policy mapping,
product-impact analysis, and obligation-level regulation diffs.

The LLM integration uses the **strategy pattern**. Agents depend on
LangChain4j's `ChatLanguageModel` interface rather than a concrete
provider. Production supplies Groq, while tests supply a routing
stub that chooses canned responses from keywords in the system
prompt. Changing the provider or test double does not require
changes to agent logic.

Saga progress is **event-sourced**, but the rest of the application
is not. Each stage appends a row to `workflow_events` with a sequence
number and JSON payload. The SSE endpoint streams those events and
honours `Last-Event-ID` when a browser reconnects. Regulations,
obligations, and matches remain conventional JPA state because
reconstructing the entire domain from events would add complexity
without helping the product.

The dashboard uses a small **read-model projection**. A
`TenantMetrics` row is refreshed after mapping and summarization,
which lets dashboard requests read precomputed totals instead of
repeating joins across the workflow tables. It is CQRS-lite rather
than a separate read service: the transactional and projected models
share Postgres but serve different query needs.

Tenant isolation is part of the data-access shape rather than a
controller-only check. The JWT filter establishes `TenantContext`,
and repository methods include `tenantId` in their derived queries,
for example `findByXxxAndTenantId(...)`. That makes a missing tenant
constraint visible at the service call site and causes cross-tenant
lookups to behave like missing records.

## The multi-vendor LLM split (a story)

I started with Groq for everything that needed an LLM call, Cohere
just for embeddings.

Then I did a full demo with a real Basel III liquidity addendum.
The Mapper churned through 13 obligations on its 4-second-per-call
rhythm, eating ~700-900 tokens each. By obligation 10 I'd burned
~8k tokens in the trailing minute. The Summarizer fired next with
the consolidation prompt — 1.5k tokens — and Groq returned a 429.
The brief never rendered.

Two changes came out of that. First, a "Try again" button on the
brief card that succeeds on the second attempt because the token
bucket has rolled. Second, I started treating the LLM layer as
something to spread across vendors deliberately. The split now:
Cohere for embeddings, Groq for the per-obligation Mapper, AWS
Bedrock wired up for the Summarizer (blocked behind a new-account
verification request — once it clears, the Summarizer moves over
and the failure mode goes away because its budget is separate from
the Mapper's).

The takeaway: free-tier capacity isn't a thing you hit then deal
with — it's a thing you design around from the start.

## Deployment

Frontend: Vercel static hosting with rewrites that proxy to the EC2.
Backend: EC2 t3.micro in us-east-1 behind a systemd service.
Database: RDS Postgres 16 with pgvector. JAR artefacts: S3, versioning
on.

CI/CD lives in `.github/workflows/ci.yml`. Every push runs tests
against a Testcontainers Postgres. Pushes to `main` also build the
boot JAR, assume an IAM role via OIDC federation (no long-lived AWS
keys in GitHub), upload to S3, then trigger an SSM Run Command on
the EC2 with a deploy script.

The deploy script swaps the JAR atomically, restarts the systemd
unit, and polls `/actuator/health` for up to 120 seconds. On
failure it restores the previous JAR and exits non-zero — a broken
deploy auto-rolls-back, the app stays up on the last good version,
the GitHub run goes red.

Security posture: port 22 closed on the EC2 (SSM Run Command
doesn't need it), no AWS credentials in GitHub (OIDC federation),
JWT signing key in an EnvironmentFile that systemd loads.

## Testing

The principle: test the real implementations, stub only the
external dependencies that cost money or are non-deterministic. I
learnt that the hard way on an earlier project that used H2 in
tests and broke on Postgres in production.

The backend currently has 85 `@Test` methods. Parser and agent tests
cover deterministic transformation and strict-JSON handling.
Workflow tests exercise complete stage transitions, while API tests
cover authentication and HTTP success and failure contracts,
including 400, 401, 404, and 409 responses.

Every Spring-context integration test boots a real Postgres 17 with
pgvector through Testcontainers. Flyway migrations run and JPA
queries execute against the same database family used in
production. Representative suites include
`ExtractionWorkflowIntegrationTest`,
`MappingWorkflowIntegrationTest`, `RegulationDiffApiTest`, and
`AuthApiTest`. The full suite runs locally and in CI with:

```bash
./gradlew test
```

The LLM stub is the bit I'm most proud of:
`RoutingStubChatLanguageModel` inspects each request's system
prompt and routes to the right canned response —
`compliance analyst` → Extractor, `compliance reviewer` → Mapper,
`taxonomist` → Impact, `compliance writer` → Summarizer,
`regulatory analyst` → Diff. One bean serves five agents in the
same Spring context. Without this I'd have ended up with five
qualifier-tagged beans and a brittle setup.

I don't claim that deterministic tests establish LLM output quality;
that would require a curated golden dataset, evaluation criteria,
and repeated model runs. That work is outside this capstone's scope.
The suite does verify the boundary I control: prompts are assembled
correctly, valid responses are parsed and persisted, malformed
responses produce the intended failure state, and retries do not
discard earlier successful work.

## Failure modes

| When | What happens |
|---|---|
| Extractor returns bad JSON | Regulation → `FAILED`, error captured, retry button in UI |
| Mapper returns bad JSON for one obligation | That obligation → `UNKNOWN`, saga continues for the rest |
| Groq rate-limits the Summarizer | Status reverts to `MAPPED` (mapping data still valid), "Try again" button on the brief |
| Cohere times out | Same as malformed-JSON path — that obligation goes UNKNOWN |
| Deploy breaks the app | Health check times out → auto-rollback to previous JAR |
| Same regulation diffed against itself | Service throws `IllegalArgumentException` → 400 |
| Cross-tenant ID slips through | Repository returns null → 404, no information leakage |
| Empty regulation | Empty brief with "no obligations found" message, not a crash |

## Things I left out on purpose

- A message broker. Five sequential stages don't need RabbitMQ.
- A separate worker process. `@Async` in the API process is fine
  for this scale.
- Distributed tracing. One process, one host. Structured logs and
  Spring Actuator cover the load.
- OCR for image-only PDFs. Most regulators publish text.
- Password-reset and invitations. Scope decisions for the
  capstone; production needs both.

## Things I added beyond the brief

- Regulation diff at the obligation level — the headline feature.
- In-app streaming AI help assistant on the Help & Docs page.
- AWS-native CI/CD with OIDC federation and auto-rollback.
- Live SSE workflow events with sequence numbers and reconnection.
- 13-topic in-app help system with searchable cards.

## Where things live

- Workflow saga: `src/main/kotlin/io/kalunge/eunomia/workflow/`
- Agents: `extraction/`, `mapping/`, `impact/`, `summarize/`,
  `diff/`, `help/` (all under `io.kalunge.eunomia`)
- Domain + JPA: `domain/`
- Auth + tenancy: `auth/`, `tenancy/`
- CI/CD: `.github/workflows/ci.yml`
- Tests: `src/test/kotlin/io/kalunge/eunomia/`
- Frontend: `web/src/`
