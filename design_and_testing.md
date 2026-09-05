# Eunomia — Design and Testing

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

The current deployment uses one Spring Boot process on EC2 and one
Postgres database on RDS. This keeps the pilot architecture small
enough to operate on low-cost infrastructure while retaining a clear
path to separate workers and additional services if usage grows.

## Tech choices that mattered

**Kotlin on Spring Boot 3.5**, because Kotlin gives me null-safety
and data classes without giving up the Spring ecosystem. Java 21
runtime; packaging the backend as one executable JAR keeps deployment
and rollback straightforward on the current 1 GB EC2 instance.

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
Cohere for embeddings and Groq for generation. Moving the
Summarizer to a second generation provider, such as AWS Bedrock,
is a future option because it would separate its rate-limit budget
from the per-obligation Mapper calls.

This incident led to explicit retry behavior and made provider
capacity a deployment consideration rather than an unhandled runtime
failure.

The end-to-end evaluation later gave this concern a measurable shape.
One successful three-obligation golden path used eight Groq calls and
7,063 tokens, plus one Cohere document-embedding request and three
query requests. Repeated evaluation runs also reached Groq's free-tier
token limits. The manual evaluation workflow therefore supports a
configurable delay between calls, and the results are treated as
capacity evidence as well as quality evidence.

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

Live model evaluation is deliberately separate in
`.github/workflows/ai-evaluation.yml`. It runs only when manually
dispatched, publishes scorecards and artifacts, and neither deploys
the application nor blocks the deterministic CI pipeline.

The deploy script swaps the JAR atomically, restarts the systemd
unit, and polls `/actuator/health` for up to 120 seconds. On
failure it restores the previous JAR and exits non-zero — a broken
deploy auto-rolls-back, the app stays up on the last good version,
the GitHub run goes red.

Security posture: port 22 closed on the EC2 (SSM Run Command
doesn't need it), no AWS credentials in GitHub (OIDC federation),
JWT signing key in an EnvironmentFile that systemd loads.

### Recommended deployment options and cost implications

The appropriate deployment depends on whether Eunomia is being used
as a demonstration, a low-volume pilot, or a production compliance
system handling regulated data.

| Option | Relative cost | Advantages | Trade-offs and recommendation |
|---|---:|---|---|
| Current Vercel + single-AZ AWS deployment | Low: $0 while eligible free tiers apply; approximately $22–25/month after expiry | Managed Postgres, simple deployment, automated backups, and enough capacity for a pilot | Recommended for the capstone demonstration and a low-volume pilot. A single EC2 instance and single-AZ database are not sufficient for a high-availability production service. |
| Hardened AWS cloud deployment | Medium: approximately $60+/month before significant LLM usage or data transfer | An Application Load Balancer, Multi-AZ RDS, stronger monitoring, and redundant application instances improve availability | Recommended cloud path for production. It costs more than the pilot architecture but reduces single points of failure and recovery time. |
| Managed platform such as Render or Railway | Low to medium, depending on database and compute tiers | Less infrastructure administration and faster setup | Suitable for prototypes and small deployments. It offers less control over networking, database extensions, deployment rollback, and data location than the AWS design. |
| Private cloud or on-premises | High initial and operational cost | Maximum control over data residency, network access, retention, and model hosting | Appropriate where regulation or bank policy prevents external cloud processing. It requires infrastructure capacity, patching, backups, monitoring, and operations staff. Self-hosted models may also require GPU hardware. |

For the current workload, the existing cloud deployment provides the
best balance of cost, maintainability, and demonstrability. A bank
moving Eunomia into production should choose either the hardened AWS
option or a private-cloud/on-premises deployment after reviewing its
data-residency, availability, and third-party AI requirements.

## Testing

The principle: test the real implementations, stub only the
external dependencies that cost money or are non-deterministic. I
learnt that the hard way on an earlier project that used H2 in
tests and broke on Postgres in production.

The Gradle suite currently executes 98 tests. Parser and agent tests
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

`RoutingStubChatLanguageModel` makes multi-agent integration tests
deterministic without changing production wiring. It inspects each
request's system prompt and routes to the appropriate canned response:
`compliance analyst` → Extractor, `compliance reviewer` → Mapper,
`taxonomist` → Impact, `compliance writer` → Summarizer,
`regulatory analyst` → Diff. One bean serves five agents in the
same Spring context, avoiding separate qualifier-tagged model beans
for each agent and keeping the test wiring consistent with production.

### AI quality evaluation

Deterministic tests prove that the application is wired correctly, but
they cannot show whether a live model extracts the right obligation or
selects the right policy clause. Eunomia therefore has a separate
`aiEval` Gradle task backed by versioned labelled datasets. It uses the
production agents and providers, writes Markdown and JSON scorecards,
and remains outside normal CI so non-deterministic calls and provider
cost do not block deployment.

| Suite | Dataset and measurements | Successful baseline |
|---|---|---|
| Extractor | Five documents; precision, recall, F1, parse success, and verbatim source faithfulness | Precision/recall/F1 `1.00`; source faithfulness `0.90` because one correct obligation used a paraphrased excerpt |
| Mapper | Ten obligation/candidate sets; classification, completeness, threshold behavior, and Brier score | 10/10 classifications; F1 `1.00`; Brier score `0.0013` |
| Diff | Three regulation pairs with ten candidate judgments; verdict, pairing, removal, and rationale checks | All measured classification, pairing, removal, and rationale metrics `1.00` |
| Retrieval | Eight queries over controlled policy chunks using real Cohere embeddings and pgvector | Recall@1 `0.9375`; Recall@3 and MRR `1.00`; tenant isolation `1.00` |
| Brief | Five mixed, all-mapped, and all-gap scenarios; structure, coverage, actions, grounding, and hallucination guards | A successful baseline passed 5/5 cases with all aggregate metrics `1.00` |
| End-to-end | One golden path through HTTP upload, indexing, extraction, retrieval, mapping, impact tagging, events, persistence, and brief generation | Core stage metrics `1.00`; about 17 seconds; eight Groq calls and 7,063 tokens |

The end-to-end case is intentionally small enough to run on demand. It
contains one stricter policy match, one direct match, and one genuine
gap, so a passing result demonstrates more than a success-only path.
The harness also records stage latency, Groq token usage, Cohere request
counts, final persisted states, and workflow-event coverage.

These results are evidence, not a guarantee. The datasets are curated
and much smaller than a bank's full regulatory corpus. Model wording
varies between runs, strict grounding checks can reject a reasonable
paraphrase, and free-tier token limits can produce provider errors
during repeated runs. For that reason the workflow report must be
inspected at case level: a green GitHub job means the harness completed,
not that every quality threshold passed. Production use would require a
larger independently reviewed dataset, periodic regression baselines,
provider capacity sized for the workload, and human approval of
compliance conclusions.

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

## Production scaling recommendations

The current architecture is appropriate for the demonstrated load,
but production adoption would justify additional capabilities:

- Introduce a durable queue and separate worker processes when
  concurrent document volume can no longer be handled reliably by
  the four in-process `@Async` workflow stages. This would preserve
  queued work across application restarts and allow workers to scale
  independently.
- Add distributed tracing if the backend is separated into multiple
  services. Structured logs and Spring Actuator are sufficient while
  requests remain inside one application process.
- Add OCR when image-only or scanned regulatory documents become a
  supported input. The current Apache Tika parser handles documents
  containing extractable text.
- Add password reset, invitations, stronger account administration,
  and enterprise identity integration before production adoption.

## Features delivered above and beyond the minimum Capstone requirements

- Regulation diff at the obligation level.
- In-app streaming AI help assistant on the Help & Docs page.
- AWS-native CI/CD with OIDC federation and auto-rollback.
- Live SSE workflow events with sequence numbers and reconnection.
- 13-topic in-app help system with searchable cards.
- Versioned AI quality evaluation for every core agent, retrieval, and
  the complete golden path.

## Where things live

- Workflow saga: `src/main/kotlin/io/kalunge/eunomia/workflow/`
- Agents: `extraction/`, `mapping/`, `impact/`, `summarize/`,
  `diff/`, `help/` (all under `io.kalunge.eunomia`)
- Domain + JPA: `domain/`
- Auth + tenancy: `auth/`, `tenancy/`
- CI/CD: `.github/workflows/ci.yml`
- Manual AI evaluation: `.github/workflows/ai-evaluation.yml`
- Tests: `src/test/kotlin/io/kalunge/eunomia/`
- Evaluation code: `src/test/kotlin/io/kalunge/eunomia/evaluation/`
- Labelled evaluation data: `src/test/resources/ai-eval/`
- Frontend: `web/src/`
