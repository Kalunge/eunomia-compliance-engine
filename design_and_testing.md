# Eunomia — Design and Testing

## What Eunomia is

When a regulator publishes a circular, a bank's compliance team has to pull out every obligation, decide which internal policies cover it, figure out which products are affected, and brief leadership.
For a typical CBK circular that's most of a senior officer's day, and every amendment means doing it again — plus spotting what changed.

Eunomia automates the whole loop. An LLM pipeline does the extraction, mapping, impact tagging, and summarising. A separate diff feature compares two regulations at the obligation level (not the text level — regulators reorder paragraphs all the time without changing substance).

Live: <https://eunomia-web.vercel.app>.

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│  Browser — React 18 + Vite + Tailwind + TanStack Query         │
└──────────────────────────────┬─────────────────────────────────┘
                               │
┌──────────────────────────────▼─────────────────────────────────┐
│  Vercel edge — static SPA + /api/*                             │
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
            + pgvector         Groq Llama 3.3 (LLM)
            on AWS RDS
```

One Spring Boot process behind one EC2, one Postgres. No broker, no Redis, no Temporal. Anything I'd add at this scale would cost more in operational weight than it'd buy me.

## Tech choices that mattered

**Kotlin on Spring Boot 3.5**, because Kotlin gives me null-safety and data classes without giving up the Spring ecosystem. Java 21 runtime; one fat JAR on a 1 GB t3.micro is the only realistic shape.

**Postgres 16 with pgvector** so relational data and vector search live in the same engine. No separate Pinecone or Qdrant to operate, back up, or pay for.

**Apache Tika** for parsing. One library covers PDF, DOCX, and plain text — compliance teams send mixed formats.

**LangChain4j** as the LLM SDK. Groq's GPT OSS 120B for completions (via the OpenAI-compatible endpoint), Cohere `embed-english-v3.0` for embeddings. I started on Llama 3.3 70B Versatile, but Groq deprecated it in June and I migrated to GPT OSS 120B before the cut-off.

**JWT HS256, 256-bit secret enforced at startup.** I learnt that length requirement the hard way — my first deploy ran with a 16-char placeholder and the app refused to issue tokens.

**AWS for backend** (EC2 + RDS + S3 + SSM) **and Vercel for the frontend.** Vercel's rewrites proxy `/api/*` to the EC2 so the browser sees same-origin — no CORS. Originally I wanted CloudFront in front of everything, but a new-account verification gate is still pending, and Vercel turned out to do the job fine.

## Patterns I used

**Saga for the workflow.** Four agents in sequence, each on its own `@Async` executor, each idempotent against its own outputs. The regulation's status field marks the last completed stage. A
re-trigger picks up from there. I looked at Temporal, but for five sequential stages with no fan-out, plugging in a separate workflow engine wasn't worth the operational weight.

**Two-stage retrieval — cosine pre-filter, then LLM judge.** Embed the obligation, ANN-search the policy chunks with pgvector, send the top-3 to the LLM with a strict-JSON prompt asking for
satisfied/not plus a confidence score. The threshold gates persistence (0.6 by default). The same pattern works for three different problems: obligation→policy (Mapper), obligation→product (ImpactAnalyzer), obligation→obligation (Diff). One architecture, three deployments.

**Strategy pattern for LLM providers.** Every agent depends on the `ChatLanguageModel` interface, not a concrete client. Production wires Groq; tests wire a routing stub that picks canned responses
from the system prompt's keywords. One bean swap, no agent code changes.

**Event sourcing for the saga progress.** Each step writes a row to `workflow_events` with a sequence number and a JSON payload. An SSE endpoint streams new events to the browser and honours `Last-Event-ID` on reconnect. The domain state (regulations, obligations, matches) is in ordinary JPA tables — only the saga progress is event-sourced. Going full event-sourcing for everything would have been ceremony.

**Read-model projection for the dashboard.** A `TenantMetrics` row per tenant is upserted at the end of every Mapper run. The dashboard reads it in O(1) instead of joining four tables on every
request. CQRS-lite — same Postgres, two query paths.

**Multi-tenancy via tenant_id on every row.** The JWT auth filter sets `TenantContext`, and every repository method takes `tenantId` as the first parameter (`findByXxxAndTenantId(...)`). Spring Data's
method-name derivation enforces it for free; reviewers catch a missing tenant filter at the call site.

## The multi-vendor LLM split (a story)

I started with Groq for everything that needed an LLM call, Cohere just for embeddings.

Then I did a full demo with a real Basel III liquidity addendum. The Mapper churned through 13 obligations on its 4-second-per-call rhythm, eating ~700-900 tokens each. By obligation 10 I'd burned ~8k tokens in the trailing minute. The Summarizer fired next with the consolidation prompt — 1.5k tokens — and Groq returned a 429. The brief never rendered.

Two changes came out of that. First, a "Try again" button on the brief card that succeeds on the second attempt because the token bucket has rolled. Second, I started treating the LLM layer as
something to spread across vendors deliberately. The split now: Cohere for embeddings, Groq for the per-obligation Mapper, AWS Bedrock wired up for the Summarizer (blocked behind a new-account
verification request — once it clears, the Summarizer moves over and the failure mode goes away because its budget is separate from the Mapper's).

The takeaway: free-tier capacity isn't a thing you hit then deal with — it's a thing you design around from the start.

## Deployment

- Frontend: Vercel static hosting with rewrites that proxy to the EC2.
- Backend: EC2 t3.micro in us-east-1 behind a systemd service.
- Database: RDS Postgres 16 with pgvector. JAR artefacts: S3, versioning on.

CI/CD lives in `.github/workflows/ci.yml`. Every push runs tests against a Testcontainers Postgres. Pushes to `main` also build the boot JAR, assume an IAM role via OIDC federation (no long-lived AWS
keys in GitHub), upload to S3, then trigger an SSM Run Command on the EC2 with a deploy script.

The deploy script swaps the JAR atomically, restarts the systemd unit, and polls `/actuator/health` for up to 120 seconds. On failure it restores the previous JAR and exits non-zero — a broken deploy auto-rolls-back, the app stays up on the last good version, the GitHub run goes red.

Security posture: port 22 closed on the EC2 (SSM Run Command doesn't need it), no AWS credentials in GitHub (OIDC federation), JWT signing key in an EnvironmentFile that systemd loads.

## Testing

The principle: test the real implementations, stub only the external dependencies that cost money or are non-deterministic. I learnt that the hard way on an earlier project that used H2 in tests and broke on Postgres in production.

Every Spring-context test boots a real Postgres 17 with pgvector through Testcontainers. Migrations run, JPA hits a real engine. What passes locally passes in CI.

The LLM stub is the bit I'm most proud of: `RoutingStubChatLanguageModel` inspects each request's system prompt and routes to the right canned response — `compliance analyst` → Extractor, `compliance reviewer` → Mapper, `taxonomist` → Impact, `compliance writer` → Summarizer, `regulatory analyst` → Diff. One bean serves five agents in the same Spring context. Without this I'd have ended up with five
qualifier-tagged beans and a brittle setup.

Coverage is layered: unit tests on the agent JSON parsers, integration tests for full saga end-to-end, API tests for HTTP contracts including 400/404/409/401 paths. I don't test LLM
output quality — that's golden-dataset work, separate from this scope. I do test that the system handles malformed LLM output gracefully, because that's the real failure mode.

## Failure modes

The system handles a range of failure modes gracefully. When the Extractor returns bad JSON, the regulation is marked FAILED, the error is captured, and a retry button appears in the UI. If the Mapper returns bad JSON for a single obligation, only that obligation is marked UNKNOWN while the saga continues for the rest. When Groq rate-limits the Summarizer, the status reverts to MAPPED (the mapping data remains valid) and a "Try again" button shows on the brief. A Cohere timeout follows the same path as malformed JSON, sending that obligation to UNKNOWN. If a deploy breaks the app, the health check times out and triggers an automatic rollback to the previous JAR. Attempting to diff the same regulation against itself causes the service to throw an IllegalArgumentException, returning a 400. Should a cross-tenant ID slip through, the repository returns null and a 404 is issued, preventing any information leakage. Finally, an empty regulation produces an empty brief with a "no obligations found" message rather than crashing.

## Things I left out on purpose

- A message broker. Five sequential stages don't need RabbitMQ.
- A separate worker process. `@Async` in the API process is fine
  for this scale.
- Distributed tracing. One process, one host. Structured logs and
  Spring Actuator cover the load.
- OCR for image-only PDFs. Most regulators publish text.
- A custom workflow engine. Camunda would be ceremony.
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
