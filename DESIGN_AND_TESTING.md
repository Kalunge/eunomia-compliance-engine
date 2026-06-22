# Eunomia — Design and Testing Document

This document accompanies the source code in this repository. It explains
the architectural decisions behind the Eunomia compliance engine, the
patterns chosen and why, the deployment design, and the testing
strategy used to validate the system.

---

## 1. What Eunomia is

Eunomia is an AI-powered banking compliance engine. When a regulator
publishes a new circular or amendment, Eunomia automatically:

- Extracts the discrete obligations (rules) from the document
- Maps each obligation against the bank's internal policies using
  semantic search plus an LLM judge
- Tags which products and services are affected by each obligation
- Composes an executive impact brief in Markdown and PDF
- Compares two versions of a regulation at the obligation level to
  surface exactly what changed across an amendment

The target market is commercial banking in prescriptive-regulator
jurisdictions (CBK in Kenya, MAS in Singapore, FCA in the UK). The
same mapping-and-gap pattern fits insurance, asset management,
fintech, payment processors, and RegTech consultancies.

A live deployment runs at <https://eunomia-web.vercel.app>.

---

## 2. Architecture at a glance

```
┌────────────────────────────────────────────────────────────────┐
│  Browser                                                       │
│  React 18 + Vite + TypeScript + Tailwind                       │
│  TanStack Query + Zustand                                      │
└──────────────────────────────┬─────────────────────────────────┘
                               │ HTTPS
┌──────────────────────────────▼─────────────────────────────────┐
│  Vercel edge — static SPA + rewrites                           │
│  /api/* and /actuator/* → EC2 backend                          │
└──────────────────────────────┬─────────────────────────────────┘
                               │
┌──────────────────────────────▼─────────────────────────────────┐
│  Spring Boot 3.5 + Kotlin 2.0 + Java 21 on AWS EC2 (us-east-1) │
│                                                                │
│  ┌── REST (api/v1/*) + SSE (text/event-stream) ──────────────┐ │
│  │  JWT auth filter → TenantContext                          │ │
│  │  Controllers → Services → Workflow orchestrator           │ │
│  └────────────────────────┬──────────────────────────────────┘ │
│                           │                                    │
│  ┌────────────────────────▼──────────────────────────────────┐ │
│  │  Multi-agent saga (one Async executor per stage)          │ │
│  │  Extractor → Mapper → ImpactAnalyzer → Summarizer         │ │
│  │  Diff agent runs out-of-band on demand                    │ │
│  └────────────────────────┬──────────────────────────────────┘ │
│                           │                                    │
│  ┌────────────────────────▼──────────────────────────────────┐ │
│  │  Persistence (Spring Data JPA + Flyway migrations)        │ │
│  └────────────────────────┬──────────────────────────────────┘ │
└───────────────────────────┼────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────────┐
        ▼                   ▼                       ▼
   Postgres 16          Cohere                  Groq
   on AWS RDS           embeddings              Llama 3.3 70B
   + pgvector           (1024-d)                completions
```

One Spring Boot process behind one EC2 instance, one Postgres on RDS,
one external embedding provider, one external completion provider.
No message broker, no Redis, no Celery, no Temporal. Every additional
service would be an attack surface, a deployment dependency, and an
operational liability for a deliverable at this scale.

---

## 3. Technology choices and rationale

| Layer | Choice | Why |
|---|---|---|
| Frontend framework | React 18 + Vite | Industry-standard SPA, fast HMR, TypeScript-first |
| Server state | TanStack Query | Caching, retry, background revalidation out of the box |
| Client state | Zustand | Tiny, no boilerplate, good for auth/JWT state |
| Styling | Tailwind | Utility-first, consistent design tokens, no CSS file sprawl |
| Backend framework | Spring Boot 3.5 | Mature ecosystem, declarative config, opinionated defaults |
| Language | Kotlin 2.0 | Null-safety, data classes, coroutines, Java interop, less boilerplate than Java |
| Runtime | Java 21 | Modern GC (G1 / ZGC), virtual threads available if needed |
| DB | Postgres 16 | JSON + relational + vector all in one engine; mature operationally |
| Vector store | pgvector | Avoids running a separate vector DB; HNSW index gives sub-linear ANN |
| Doc parsing | Apache Tika | One library handles PDF, DOCX, plain text without vendor coupling |
| LLM (completions) | Groq Llama 3.3 70B | Fastest-token-rate provider in 2026; OpenAI-compatible API |
| LLM (embeddings) | Cohere `embed-english-v3.0` | 1024-d L2-normalised vectors with separate `search_document` / `search_query` modes |
| LLM orchestration | LangChain4j | Established Kotlin/Java SDK; strategy pattern over providers |
| Auth | JWT (HS256) | Stateless, no session store, scales horizontally |
| PDF render | openhtmltopdf | Pure-Java, no headless Chrome, fits a 1 GB instance |
| Frontend hosting | Vercel | Free static hosting with rewrites that proxy to the EC2 backend |
| Backend hosting | AWS EC2 t3.micro | Free Tier eligible; sized for capstone demo, not production scale |
| Database hosting | AWS RDS Postgres | Managed backups, point-in-time recovery, Free Tier eligible |
| Object storage | AWS S3 | Versioned JAR artefact storage for the CI/CD pipeline |
| CI/CD | GitHub Actions + AWS SSM | Native OIDC federation to AWS, no long-lived credentials |

The defining principle across these choices: **avoid optionality cost.**
Anywhere a single mature tool can do the job, we pick it rather than
introduce an alternative for theoretical flexibility.

---

## 4. Architectural patterns in active use

The rubric grades named architectural patterns and the reasoning behind
them. Six patterns are in active use:

### 4.1 Saga (process manager)

A regulatory document is processed by four agents in sequence. Each
agent is a side-effectful operation: an LLM call, a vector search, a
database write. Step 3 failing must not unravel steps 1 and 2 — the
work they did is durable and re-running from step 3 should be cheap.

Implemented as an `@Async`-tagged service per stage. Each stage is
idempotent against its own outputs (e.g. the Mapper deletes existing
matches for the obligation before writing new ones). Failure leaves
the regulation in an intermediate state (e.g. `EXTRACTED` after the
Extractor succeeded but the Mapper crashed); the system can resume
from the persisted state on the next request.

This is deliberately not a Camunda / Temporal workflow engine. For
five sequential steps with no fan-out or parallelism, adding a
workflow engine would be ceremony, not value.

### 4.2 Strategy pattern for LLM providers

Every agent depends on the LangChain4j `ChatLanguageModel` interface,
not a concrete provider. The production binding is a Groq endpoint
behind the OpenAI-compatible adapter (`OpenAiChatModel`). The test
binding is `RoutingStubChatLanguageModel` (see §6.4). Swapping the
provider is one bean change, not a code rewrite.

The same applies to embeddings: agents depend on `EmbeddingService`.
The production binding wraps Cohere; the test binding produces
deterministic 1024-d L2-normalised vectors.

### 4.3 Two-stage retrieval (cosine pre-filter + LLM judge)

Used in three different places at three different vectors:

| Agent | Source | Target | Output |
|---|---|---|---|
| Mapper | Obligation | Policy chunks | "Which clauses satisfy this rule?" |
| ImpactAnalyzer | Obligation | Product catalogue | "Which products are affected?" |
| Diff | Candidate obligation | Baseline obligations | "Unchanged / modified / added?" |

Stage one is sub-linear: embed the source, ANN-search the target by
cosine distance, take top-K. Stage two is precise but expensive:
send the K candidates to the LLM and ask for a structured judgement
(satisfied / not, with confidence). The threshold gates persistence.

The same pattern, three deployments. Implementing it once and reusing
the architecture three ways was a deliberate choice.

### 4.4 Event sourcing (for the live activity feed)

The user interface watches the saga progress in real time. A status
field would tell users *what state the regulation is in now*, but not
*what has happened so far*. The system records each saga step as a
`WorkflowEvent` row with a sequence number and a JSON payload. The
SSE endpoint streams new events to subscribed clients as they're
emitted, and replays missed events on reconnect using the
`Last-Event-ID` header.

Event sourcing here is scoped — only the saga progress is event-sourced.
Domain state (regulations, obligations, policy matches) lives in
ordinary JPA tables.

### 4.5 CQRS-lite read projections

The dashboard's "regulations processed", "open gaps", "affected products"
counts must update synchronously after each saga completes — but
computing them on every dashboard request would be slow. A
`TenantMetrics` row per tenant is upserted at the end of each Mapper
run with the precomputed counts. The dashboard reads this row in O(1).

CQRS-lite because we run one Postgres, not two services — but the
read-model approach is the same.

### 4.6 Multi-tenant row-level isolation

One Postgres, one shared schema, a `tenant_id` column on every domain
table. The JWT auth filter extracts `tenantId` from the token, puts
it on a `TenantContext` holder, and every repository method takes
`tenantId` as its first parameter (the convention is enforced by the
method-name derivation in Spring Data — `findByXxxAndTenantId`).

The trade-off relative to schema-per-tenant: simpler operations,
fewer connection-pool resources, but a stricter discipline on
queries. The discipline is enforced at the repository signature
level so a code reviewer (or the compiler) catches a missing
`tenantId` parameter at the call site.

---

## 5. Multi-vendor LLM strategy

The system splits LLM responsibilities across two vendors:

- **Cohere** — embeddings (`embed-english-v3.0`, 1024-d, L2-normalised,
  with separate `search_document` and `search_query` modes)
- **Groq** — completions (Llama 3.3 70B via the OpenAI-compatible
  REST API, with retry and back-off for rate-limit responses)

This split exists because single-vendor free-tier capacity was fragile
under the saga's load profile. Groq's free tier caps tokens-per-minute
across the account; one batch of Mapper calls can leave the
Summarizer too little headroom to finish, causing the saga's last
step to 429-fail. A multi-vendor architecture is the resilient
shape — and a real-world observation that informs the design, not a
theoretical defence.

The system is also wired to support **AWS Bedrock** as a future
third vendor for the Summarizer specifically. The streaming chat
model bean used by the in-app help assistant is already a streaming
LangChain4j adapter, ready to point at Bedrock once account
verification clears.

---

## 6. Testing strategy

The testing pyramid is built around the principle of testing the
**real implementations** wherever practical and stubbing only the
external dependencies that cost money or are non-deterministic.

### 6.1 Test layers

| Layer | What's exercised | What's stubbed |
|---|---|---|
| Unit tests | Pure functions, parsers, value-type construction | Nothing |
| Agent tests | Production agent + JSON parsing + prompt structure | LLM only (`StubChatLanguageModel`) |
| Service tests | Service-level orchestration logic | LLM + embeddings |
| Integration tests | Full Spring context + real Postgres + JPA + auth | LLM + embeddings |
| End-to-end | Full saga from upload to brief | LLM + embeddings (the saga itself is real) |

### 6.2 Testcontainers for the database

Every Spring-context test boots a real Postgres with pgvector through
Testcontainers (`pgvector/pgvector:pg17`). Migrations run, the schema
is built, and tests interact with a real JPA + JDBC stack. No
H2-vs-Postgres skew; what passes locally passes in CI.

### 6.3 Stub embedding service

`StubEmbeddingService` produces deterministic 1024-dimensional
L2-normalised vectors from input text using a hash-based projection.
This avoids burning Cohere quota in CI while preserving the property
that similar inputs produce similar vectors — enough for ANN
ordering to behave realistically in integration tests.

### 6.4 Routing stub for the LLM

`RoutingStubChatLanguageModel` is the single LLM stub bean used
across every test that loads the Spring context. It inspects the
system prompt of each request and returns the appropriate canned
response for that agent:

| System prompt keyword | Canned response |
|---|---|
| "compliance analyst" | Extractor JSON |
| "compliance reviewer" | Mapper JSON |
| "taxonomist" | ImpactAnalyzer JSON |
| "compliance writer" | Summarizer markdown |
| "regulatory analyst" | Diff JSON |

This pattern keeps the production code unchanged in tests — every
agent still depends on `ChatLanguageModel`, the production wiring is
identical, only the bean's implementation differs by Spring profile.

### 6.5 Test inventory (illustrative — not exhaustive)

| File | What it covers |
|---|---|
| `LangChain4jExtractorAgentTest` | Extractor JSON parse paths, markdown-fence tolerance, malformed input |
| `LangChain4jMapperAgentTest` | Mapper judgement parsing, candidate indexing, confidence parsing |
| `LangChain4jImpactAnalyzerAgentTest` | Product-tagging JSON parsing, edge cases |
| `LangChain4jSummarizerAgentTest` | Brief generation, retry on rate limit |
| `LangChain4jDiffAgentTest` | Diff verdicts, baselineIndex normalisation, self-contradiction recovery |
| `ExtractionWorkflowIntegrationTest` | Upload → PARSING → EXTRACTED through the full stack |
| `MappingWorkflowIntegrationTest` | Mapper assigning matches/gaps, transition to MAPPED |
| `RegulationDiffApiTest` | POST /api/v1/diff happy path + 400/404/409/401 |
| `DashboardApiTest` | KPI endpoint after a full saga run, with metrics recomputed |
| Domain repository tests | Multi-tenant query isolation invariants |

### 6.6 What is intentionally not tested

- **The third-party LLM responses themselves.** We don't assert that
  Groq's output is "good" — we assert that the system processes well-
  formed and ill-formed LLM outputs correctly.
- **The Vercel rewrite layer.** It's owned by Vercel and configured
  declaratively.
- **The browser SSE consumer's exact reconnection semantics.** That's
  EventSource standard behaviour. We test that the backend honours the
  `Last-Event-ID` header, which is the part we own.

### 6.7 Continuous integration

The `test` job in `.github/workflows/ci.yml` runs on every push to any
branch and every PR targeting `main`:

1. Checkout
2. Set up JDK 21 (Temurin)
3. Restore Gradle cache
4. `./gradlew build -x test` (build artefacts + compile tests)
5. `./gradlew test` (run all tests against Testcontainers)
6. Upload test reports as artefacts on failure

Tests must pass before any deploy job fires. The deploy job is gated
on `needs: test` and `if: github.ref == 'refs/heads/main'`.

---

## 7. Deployment design

### 7.1 Infrastructure topology

| Resource | Service | Region |
|---|---|---|
| Frontend (static SPA) | Vercel | global edge |
| Backend (Spring Boot) | AWS EC2 t3.micro | us-east-1 |
| Database | AWS RDS Postgres 16 | us-east-1 |
| JAR artefact bucket | AWS S3 | us-east-1 |
| IAM federation | GitHub OIDC ↔ AWS STS | n/a |

The frontend's `_redirects` (Render era) and `vercel.json` (current)
proxy `/api/*` and `/actuator/*` to the EC2 public IP so the SPA
sees a same-origin API. No CORS configuration is required.

### 7.2 Continuous delivery

The `build-and-upload` job in `.github/workflows/ci.yml` runs on
pushes to `main` after `test` passes:

1. Set up JDK 21
2. Build `bootJar`
3. Assume the `eunomia-github-deployer` IAM role via OIDC federation
   (no long-lived AWS credentials are stored in GitHub)
4. Upload the JAR to `s3://eunomia-deploys-<account>/backend/` under
   two keys: `app-<sha>.jar` (immutable, traceable) and `latest.jar`
   (the pointer the EC2 will pull)
5. Issue an `aws ssm send-command` to the EC2 instance with a deploy
   script

The deploy script (running as root on EC2 via the SSM agent):

1. Downloads `latest.jar` to a staging path
2. Validates the JAR is non-zero
3. Backs up the current `/home/ec2-user/app.jar` to `app.jar.prev`
4. Atomically swaps in the new JAR
5. Restarts the `eunomia` systemd service
6. Polls `/actuator/health` every 5 s for up to 120 s
7. On success, exits 0; on health-check timeout, restores
   `app.jar.prev`, restarts the service, and exits non-zero

The auto-rollback step matters: a deploy that breaks the application
restores the last known-good JAR without manual intervention, and
the GitHub Actions workflow reports the failure.

### 7.3 Security posture

- **No SSH from the runner to EC2.** The deploy uses SSM Run Command;
  port 22 is closed on the EC2 security group.
- **No AWS credentials in GitHub.** OIDC federation issues short-lived
  STS credentials per workflow run.
- **JWT signing key** is a 256-bit secret stored as an EC2 environment
  file and never committed.
- **Spring Security filter chain** denies all `/api/v1/*` requests
  without a valid JWT. The auth filter sets `TenantContext` from the
  token's `tenantId` claim.

---

## 8. Failure modes explicitly designed for

| Failure | What happens | What the user sees |
|---|---|---|
| Extractor LLM returns malformed JSON | `ExtractionParseException`; regulation moves to `FAILED` | Status badge on the regulation card, retry button |
| Mapper LLM returns malformed JSON for one obligation | That obligation's `gap_status` becomes `UNKNOWN`; saga continues | Obligation card shows UNKNOWN; rest of brief is unaffected |
| Groq rate-limit hits the Summarizer | Brief generation fails; status reverts to `MAPPED` (not `FAILED`) because mapping data is still valid | "Try again" button on the brief card |
| Cohere times out | Mapper logs warning; that obligation goes to `UNKNOWN` | Same as malformed JSON path above |
| EC2 health check fails after deploy | Auto-rollback restores previous JAR | No user-visible breakage |
| Empty regulation (no obligations) | Saga completes with zero obligations; brief reads "no obligations found" | Brief renders with the empty-state copy |
| Diff between same regulation | `RegulationDiffService` rejects with `IllegalArgumentException` → 400 | UI prevents the selection; backend defends in depth |
| Cross-tenant ID in any request | `findByIdAndTenantId` returns null → 404 | Treated identically to a missing resource — no information leakage |

---

## 9. What was consciously not built

Listed honestly because the design defence depends on knowing the
limits:

- **A message broker** (RabbitMQ, Kafka, SQS) — the saga is small
  enough to run in-process. Adding a broker would be over-engineering.
- **A separate worker process** — agent work runs on `@Async` executors
  in the API process. A production-scale deployment would split
  workers off for independent scaling; not justified at capstone
  scale.
- **Distributed tracing** — a single-process deployment doesn't
  benefit. Structured logs + Spring Boot Actuator metrics are
  sufficient.
- **OCR for image-only PDFs** — most regulators publish text-based
  PDFs; OCR adds operational complexity for an edge case.
- **A custom workflow engine** — Camunda or Temporal would be
  ceremony for five sequential steps.
- **Multi-language prompts** — English only. Multilingual support
  would require translated prompts and a multilingual embedder.
- **A password-reset flow** — the JWT auth is intentionally minimal
  for the capstone scope.
- **An invitations flow** — teammates self-register with the same
  tenant slug. Real production would need invitation tokens.

Each absence is a deliberate scope decision documented here so the
grader can see the trade-off was made consciously, not by omission.

---

## 10. Above-and-beyond features

Beyond the minimum capstone requirements:

- **Regulation diff** — obligation-level comparison between two
  regulations with paraphrase tolerance, threshold-change detection,
  and per-pair rationale. This is the explicit "answer a question
  nobody else's project answers" feature from the original design
  notes.
- **In-app streaming AI help assistant** — token-by-token SSE
  streaming of LLM responses on the Help & Docs page, with example
  question chips, conversation history, and per-turn error
  handling.
- **AWS-native CI/CD with OIDC federation** — no long-lived AWS
  credentials in GitHub, SSM Run Command for deploys (no SSH), S3
  versioning for artefact retention, atomic JAR swap with
  auto-rollback.
- **Live SSE workflow events with reconnection** — sequence-numbered
  events with `Last-Event-ID` honouring for cleanly resuming after
  a network blip or page reload.
- **Multi-vendor LLM split** — Cohere for embeddings, Groq for
  completions; AWS Bedrock wired and ready to take over
  Summarizer-class workloads once account verification clears.
- **Comprehensive in-app help system** — 13 documented topics in a
  searchable card grid with expandable detailed guides.

---

## 11. References

The repository is organised so the grader can verify every claim above
against the source:

- **Workflow saga** — `src/main/kotlin/io/kalunge/eunomia/workflow/`
- **Agents** — `src/main/kotlin/io/kalunge/eunomia/extraction/`,
  `mapping/`, `impact/`, `summarize/`, `diff/`, `help/`
- **Domain entities + JPA repositories** —
  `src/main/kotlin/io/kalunge/eunomia/domain/`
- **Multi-tenant filter + auth** —
  `src/main/kotlin/io/kalunge/eunomia/tenancy/`,
  `src/main/kotlin/io/kalunge/eunomia/auth/`
- **CI/CD pipeline** — `.github/workflows/ci.yml`
- **Tests** — `src/test/kotlin/io/kalunge/eunomia/`
- **Frontend SPA** — `web/src/`

The deployed system is at <https://eunomia-web.vercel.app> and the
backend health endpoint at <http://100.56.250.136:8080/actuator/health>.
