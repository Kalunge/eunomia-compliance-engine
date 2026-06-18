import { useMemo, useState, type ReactNode } from 'react'
import {
  Activity,
  BookOpen,
  ChevronDown,
  ChevronUp,
  Code,
  FileText,
  GitCompare,
  LayoutDashboard,
  ListChecks,
  PlayCircle,
  Search,
  Shield,
  Target,
  Upload,
  Users,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { clsx } from 'clsx'

// ───── Types ─────────────────────────────────────────────────────

type Section =
  | { kind: 'steps'; title: string; items: string[] }
  | { kind: 'list'; title: string; items: string[] }
  | { kind: 'paragraph'; title: string; text: string }
  | { kind: 'tip'; text: string }
  | { kind: 'warn'; text: string }
  | { kind: 'table'; title: string; headers: [string, string]; rows: [string, string][] }

interface HelpTopic {
  id: string
  title: string
  description: string
  icon: LucideIcon
  sections: Section[]
}

// ───── Content ───────────────────────────────────────────────────
// Topic content lives here as data so the page renderer is a simple
// dispatch. Add a new topic by adding an object to TOPICS and the
// search/cards/guides regenerate automatically.

const TOPICS: HelpTopic[] = [
  {
    id: 'getting-started',
    title: 'Getting Started',
    description: 'Create an account, sign in, and find your way around the platform.',
    icon: PlayCircle,
    sections: [
      {
        kind: 'steps',
        title: 'Create an account',
        items: [
          'Open the **Eunomia** sign-in page and click **Register**.',
          'Enter a **bank slug** — a short, URL-safe identifier for your organisation (e.g. `acme-bank`). Use lowercase letters, digits, and dashes only.',
          'Enter your **email**, a **password** (minimum 12 characters), and your **display name**.',
          'Click **Create account**. You will be signed in and redirected to the **Dashboard**.',
        ],
      },
      {
        kind: 'tip',
        text: 'The bank slug identifies your tenant. Every regulation, policy, user, and brief you create stays inside this tenant — no other organisation can see it.',
      },
      {
        kind: 'steps',
        title: 'Sign in to an existing account',
        items: [
          'Enter your **bank slug**, **email**, and **password** on the sign-in page.',
          'Click **Sign in**. You will land on the **Dashboard**.',
          'Your session lasts 24 hours. After that, sign in again.',
        ],
      },
      {
        kind: 'paragraph',
        title: 'Finding your way around',
        text: 'The sidebar on the left has every primary destination: Dashboard, Regulations, Compare regulations, Internal policies, and Settings. Your profile, tenant slug, and the Sign out button sit at the bottom of the sidebar.',
      },
      {
        kind: 'tip',
        text: 'On mobile, the sidebar collapses to a top bar. Tap the menu icon to reveal navigation.',
      },
      {
        kind: 'warn',
        text: 'Self-service password reset is not yet available. If you forget your password, contact your tenant administrator or register a new account.',
      },
    ],
  },
  {
    id: 'dashboard',
    title: 'Dashboard',
    description: 'A live view of your bank\'s regulatory coverage at a glance.',
    icon: LayoutDashboard,
    sections: [
      {
        kind: 'paragraph',
        title: 'What the Dashboard shows',
        text: 'The Dashboard is a one-screen summary of your compliance posture. Three KPI cards and a recent-activity feed.',
      },
      {
        kind: 'table',
        title: 'The three KPI cards',
        headers: ['Card', 'What it counts'],
        rows: [
          ['Regulations processed', 'How many uploaded regulations have completed the full agent saga and have a brief ready.'],
          ['Open gaps', 'How many obligations across all regulations have no internal policy clause that satisfies them.'],
          ['Affected products', 'How many distinct products or services across the bank are touched by at least one obligation.'],
        ],
      },
      {
        kind: 'paragraph',
        title: 'Recent activity',
        text: 'Shows the most recent regulations you uploaded with their obligation count and brief status. Click any row to jump into the regulation\'s detail page.',
      },
      {
        kind: 'tip',
        text: 'KPIs refresh automatically when each regulation finishes mapping. If a count looks stale, hard-refresh the page (Cmd+Shift+R / Ctrl+Shift+R).',
      },
    ],
  },
  {
    id: 'upload-regulation',
    title: 'Uploading Regulations',
    description: 'Add a regulatory document for Eunomia to extract, map, and brief.',
    icon: Upload,
    sections: [
      {
        kind: 'steps',
        title: 'Upload a regulation',
        items: [
          'Click **Regulations** in the sidebar, then **Upload regulation** in the top-right.',
          'Click **Choose file** and pick a regulatory document from your computer.',
          '(Optional) Enter a **title**. If left blank, Eunomia uses the filename.',
          'Click **Upload**. You will see the regulation appear in the list with status **PARSING**.',
        ],
      },
      {
        kind: 'list',
        title: 'Supported formats',
        items: [
          'PDF (preferred — most regulators publish this)',
          'Microsoft Word (.docx, .doc)',
          'Plain text (.txt, .md)',
          'Maximum file size: 25 MB',
        ],
      },
      {
        kind: 'warn',
        text: 'Image-only PDFs (scanned documents) are not supported. The text-extraction layer needs machine-readable text. If your regulator only publishes scans, run them through an OCR tool first.',
      },
      {
        kind: 'paragraph',
        title: 'What happens after upload',
        text: 'Eunomia parses the document, extracts the list of obligations using an LLM, maps each obligation against your internal policies, tags affected products, and composes an executive impact brief. The whole pipeline typically takes 60-90 seconds and runs in the background — you can leave the page and come back.',
      },
      {
        kind: 'tip',
        text: 'Upload at least one internal policy before your first regulation so the mapping step has something to compare against. Otherwise every obligation will surface as a gap.',
      },
    ],
  },
  {
    id: 'live-activity',
    title: 'Live Agent Activity',
    description: 'Watch the AI agents work through your document in real time.',
    icon: Activity,
    sections: [
      {
        kind: 'paragraph',
        title: 'What the activity feed is',
        text: 'Open any regulation detail page while the saga is running. The **Live agent activity** panel streams events from the backend as each agent completes a step. This exists so you can verify the system is actually doing the work — not hand-waving with an LLM.',
      },
      {
        kind: 'table',
        title: 'Event reference',
        headers: ['Event', 'What it means'],
        rows: [
          ['document.uploaded', 'File received and persisted to storage.'],
          ['extractor.start', 'The Extractor agent is about to call the LLM with the document text.'],
          ['document.parsed', 'Text extraction finished — character count visible on hover.'],
          ['extractor.identified', 'LLM returned N obligations from the document.'],
          ['mapper.start', 'Mapper agent starting — about to process each obligation.'],
          ['mapping.obligation.start', 'One obligation is being processed against the policy catalogue.'],
          ['candidates.retrieved', 'Vector search returned the top-K closest policy clauses.'],
          ['match.found', 'LLM judge confirmed at least one policy clause satisfies the obligation.'],
          ['gap.detected', 'No policy clause satisfies this obligation — it\'s a gap.'],
          ['mapping.complete', 'Mapper finished all obligations.'],
          ['impact.start', 'Impact analyser is about to tag affected products.'],
          ['products.tagged', 'One obligation\'s product impact is recorded.'],
          ['impact.complete', 'Impact tagging finished for the regulation.'],
          ['summarizer.start', 'Composing the executive impact brief.'],
          ['brief.ready', 'Brief is rendered and ready to read.'],
        ],
      },
      {
        kind: 'tip',
        text: 'The feed reconnects automatically if you refresh the page mid-saga. No events are lost — the backend persists a sequence number and replays anything you missed.',
      },
      {
        kind: 'paragraph',
        title: 'Why the saga takes 60-90 seconds',
        text: 'The Mapper agent deliberately waits 4 seconds between obligations to stay under the LLM provider\'s rate limit. A regulation with 10 obligations therefore takes ~40 seconds in the Mapper alone, plus ~10 seconds for extraction and ~20 seconds for the brief.',
      },
    ],
  },
  {
    id: 'impact-brief',
    title: 'Impact Briefs',
    description: 'Read, download, and regenerate the executive summary of any regulation.',
    icon: FileText,
    sections: [
      {
        kind: 'paragraph',
        title: 'What the brief is',
        text: 'After all obligations are mapped and impacts tagged, the Summarizer agent composes a short, executive-readable document explaining what the regulation requires, where your bank is exposed, and what to do next. The brief is markdown, rendered inline on the regulation detail page.',
      },
      {
        kind: 'list',
        title: 'Typical brief sections',
        items: [
          'Executive summary — one or two paragraphs framing the regulation for non-technical readers.',
          'Obligations imposed — itemised list with each rule\'s required action.',
          'Policy coverage — what is mapped versus what is a gap.',
          'Recommended actions — concrete next steps to close gaps.',
        ],
      },
      {
        kind: 'steps',
        title: 'Download the brief as PDF',
        items: [
          'On the regulation detail page, locate the **Impact Brief** card.',
          'Click **Download PDF**. The PDF is rendered server-side and downloads to your browser.',
          'File name follows the pattern `<regulation-title>-impact-brief.pdf`.',
        ],
      },
      {
        kind: 'steps',
        title: 'Regenerate the brief',
        items: [
          'Click **Regenerate** on the Impact Brief card.',
          'The Summarizer re-runs with the same obligations and produces a fresh draft.',
          'The page will show a spinner during the rebuild. The download stays available until the new version is ready.',
        ],
      },
      {
        kind: 'tip',
        text: 'If brief generation fails with a rate-limit error, click **Try again** on the brief card. The next attempt fires after the rate-limit window rolls.',
      },
    ],
  },
  {
    id: 'obligations',
    title: 'Obligations & Gaps',
    description: 'Understand each extracted rule and whether your bank has it covered.',
    icon: ListChecks,
    sections: [
      {
        kind: 'paragraph',
        title: 'What an obligation is',
        text: 'An obligation is one discrete requirement extracted from a regulation — usually one sentence stating a "shall" or "must" rule. Eunomia stores each obligation as its own record with the LLM\'s paraphrased summary, the verbatim source excerpt from the regulation, and metadata about where in the document it came from.',
      },
      {
        kind: 'paragraph',
        title: 'Why two fields per obligation',
        text: 'The **summary** is the Extractor agent\'s paraphrase — short and consistent across obligations. The **source excerpt** is verbatim regulatory text. Both are shown together so you can verify the LLM did not drift in paraphrasing.',
      },
      {
        kind: 'table',
        title: 'Gap status meanings',
        headers: ['Status', 'Meaning'],
        rows: [
          ['MAPPED', 'At least one internal policy clause satisfies this obligation above the confidence threshold.'],
          ['GAP', 'No internal policy clause satisfies this obligation. Action required.'],
          ['UNKNOWN', 'Mapping step did not complete for this obligation (technical failure). Re-running the regulation will retry.'],
        ],
      },
      {
        kind: 'paragraph',
        title: 'Reading the obligation card',
        text: 'Each card shows the summary on top, the source excerpt below in italics, the mapped policy chunks (if any) with confidence percentages, and a chip showing affected products. Click any policy chunk to jump into the source policy.',
      },
      {
        kind: 'tip',
        text: 'A "GAP" does not mean your bank is non-compliant — only that Eunomia could not find an internal policy that explicitly covers the obligation. The control may exist in operational procedures outside the indexed policy catalogue.',
      },
    ],
  },
  {
    id: 'policies',
    title: 'Internal Policies',
    description: 'Upload your bank\'s policies so the system can map obligations against them.',
    icon: BookOpen,
    sections: [
      {
        kind: 'paragraph',
        title: 'Why you upload internal policies',
        text: 'The Mapper agent needs a searchable catalogue of your internal controls to match obligations against. Without policies, every obligation surfaces as a gap. The more comprehensive your policy catalogue, the more accurate the mapping.',
      },
      {
        kind: 'steps',
        title: 'Upload a policy',
        items: [
          'Click **Internal policies** in the sidebar.',
          'Click **Upload policy** in the top-right.',
          'Pick a PDF, Word, or text file representing an internal control document (KYC procedures, AML policy, data retention policy, loan origination policy, etc.).',
          '(Optional) Enter a **title**. Defaults to the filename.',
          'Click **Upload**. The policy appears in the list with status **PARSING**.',
        ],
      },
      {
        kind: 'paragraph',
        title: 'What happens after upload',
        text: 'Eunomia extracts the text, splits it into ~500-token chunks with overlap, embeds each chunk into a vector, and stores them with a database index that supports fast nearest-neighbour search. Status moves from **PARSING** to **INDEXED** when ready.',
      },
      {
        kind: 'paragraph',
        title: 'Viewing policy chunks',
        text: 'Click any policy in the list to see all extracted chunks. This is useful for verifying the indexer captured the right text and for understanding what the Mapper will compare obligations against.',
      },
      {
        kind: 'tip',
        text: 'Re-uploading a policy creates a new policy row — it does not overwrite the previous version. To replace a policy, upload the new version and ignore the old one. Future versions of Eunomia will support explicit policy versioning.',
      },
    ],
  },
  {
    id: 'mapping',
    title: 'Mapping & Confidence',
    description: 'How obligations are matched to internal policies and what confidence means.',
    icon: Target,
    sections: [
      {
        kind: 'paragraph',
        title: 'The two-stage mapper',
        text: 'Mapping every obligation against every policy clause with an LLM would be slow and expensive. Eunomia uses a two-stage approach: a fast vector search narrows the candidate pool to the closest K clauses, then an LLM judge reads the candidates and the obligation together and decides whether each clause genuinely satisfies the obligation.',
      },
      {
        kind: 'list',
        title: 'How a mapping decision is made',
        items: [
          'The obligation summary is embedded into a 1024-dimensional vector using Cohere\'s embedder.',
          'The vector is compared against every indexed policy chunk in your tenant using cosine distance.',
          'The top 3 closest chunks (configurable) are sent to the LLM judge along with the obligation.',
          'The judge returns a satisfaction verdict and a confidence score (0.0-1.0) for each candidate.',
          'Candidates above the confidence threshold (default 0.6) are persisted as matches.',
          'If no candidate clears the threshold, the obligation is marked **GAP**.',
        ],
      },
      {
        kind: 'paragraph',
        title: 'What confidence means',
        text: 'Confidence is the LLM\'s self-reported certainty that the policy clause satisfies the obligation. 0.6+ is the threshold for a persisted match; values above 0.9 are typically near-verbatim matches; values between 0.6 and 0.8 are paraphrases or stricter controls that still satisfy the obligation.',
      },
      {
        kind: 'tip',
        text: 'Multiple policy clauses can satisfy the same obligation. The obligation card shows all matches above threshold, sorted by confidence.',
      },
    ],
  },
  {
    id: 'compare-regulations',
    title: 'Compare Regulations',
    description: 'Diff two versions of a regulation at the obligation level to surface what changed.',
    icon: GitCompare,
    sections: [
      {
        kind: 'paragraph',
        title: 'When to use the diff',
        text: 'Regulators frequently issue amendments to existing circulars. Instead of reading two PDFs side by side to spot the differences, run a diff: Eunomia compares the two regulations\' obligations and tells you what changed, what was added, what was removed, and what stayed the same.',
      },
      {
        kind: 'steps',
        title: 'Run a comparison',
        items: [
          'Click **Compare regulations** in the sidebar.',
          'In the **Baseline** dropdown, pick the older version of the regulation.',
          'In the **Candidate** dropdown, pick the newer version. The baseline selection is disabled in this dropdown so you cannot pick the same regulation twice.',
          'Click **Run comparison**.',
          'Wait 40-60 seconds for the diff to complete. A spinner explains what is happening.',
        ],
      },
      {
        kind: 'table',
        title: 'The four result buckets',
        headers: ['Bucket', 'Meaning'],
        rows: [
          ['Unchanged', 'Candidate obligation restates the same operational rule as a baseline obligation. Wording may differ but the substance is equivalent.'],
          ['Modified', 'Candidate obligation is clearly the same rule as a baseline, but a threshold, timeframe, scope, or condition has changed. Rationale explains what shifted.'],
          ['Added', 'Candidate obligation has no match in the baseline. New requirement.'],
          ['Removed', 'Baseline obligation has no match in the candidate. Requirement dropped.'],
        ],
      },
      {
        kind: 'paragraph',
        title: 'The material change score',
        text: 'The percentage at the top of the result is (modified + added + removed) divided by the larger of the two obligation counts. 0% means the amendment changed nothing material; 100% means the candidate is essentially a new regulation. The banner colour reflects severity: slate (0%), emerald (<20%), amber (<60%), rose (≥60%).',
      },
      {
        kind: 'warn',
        text: 'Both regulations must have completed mapping (status MAPPED or later) to appear in the dropdowns. If a regulation is still being processed, wait for it to finish.',
      },
      {
        kind: 'tip',
        text: 'Diff results are computed on demand — nothing is cached. Re-running the same pair is cheap and always reflects the current state of both regulations.',
      },
    ],
  },
  {
    id: 'security',
    title: 'Account & Security',
    description: 'How your account is protected and how sessions work.',
    icon: Shield,
    sections: [
      {
        kind: 'paragraph',
        title: 'How sign-in works',
        text: 'Eunomia uses JWT-based authentication. When you sign in, the server issues a signed token that your browser stores locally. Every subsequent API request includes this token in the Authorization header. The server validates the signature, checks expiry, and resolves the tenant context.',
      },
      {
        kind: 'list',
        title: 'Password requirements',
        items: [
          'Minimum 12 characters.',
          'Hashed with BCrypt before storage — the server never sees your plaintext password.',
          'Stored per-tenant: two users at different tenants can share an email/password without conflict.',
        ],
      },
      {
        kind: 'paragraph',
        title: 'Session expiry',
        text: 'Tokens expire 24 hours after sign-in. After expiry, your next API call returns 401 and you are automatically redirected to the sign-in page. Refresh tokens are not yet supported; treat your session as a single-day window.',
      },
      {
        kind: 'steps',
        title: 'Sign out manually',
        items: [
          'Click the **Sign out** button at the bottom of the sidebar.',
          'Your local token is cleared and you are redirected to the sign-in page.',
        ],
      },
      {
        kind: 'tip',
        text: 'For maximum security on shared computers, always sign out manually instead of just closing the tab. Closing the tab leaves the token in browser storage for 24 hours.',
      },
    ],
  },
  {
    id: 'tenancy',
    title: 'Tenants & Multi-User',
    description: 'How tenant isolation works and how multiple users share an organisation.',
    icon: Users,
    sections: [
      {
        kind: 'paragraph',
        title: 'What a tenant is',
        text: 'A tenant is your organisation\'s isolated workspace in Eunomia. Each tenant has its own set of regulations, internal policies, users, and product catalogue. No data leaks across tenants — even an administrator at one tenant cannot see another tenant\'s data.',
      },
      {
        kind: 'paragraph',
        title: 'The tenant slug',
        text: 'Every tenant has a URL-safe slug like `acme-bank` that identifies it. The slug appears in the sign-in form, in the sidebar footer, and (in future versions) in custom subdomains.',
      },
      {
        kind: 'paragraph',
        title: 'How users share a tenant',
        text: 'Multiple users at the same organisation register with the same slug. As long as the slug matches, all their actions land in the shared tenant — they see the same dashboard, the same regulations, the same policies. Each user authenticates with their own email and password.',
      },
      {
        kind: 'warn',
        text: 'Invitation-based user provisioning is not yet implemented. To add a teammate, share the tenant slug with them and have them register themselves.',
      },
      {
        kind: 'tip',
        text: 'The first user who registers a slug effectively owns that tenant. Pick a slug carefully — there is no rename flow yet.',
      },
    ],
  },
  {
    id: 'api-access',
    title: 'API Access & Integrations',
    description: 'Use the Eunomia API directly from scripts, dashboards, or other tools.',
    icon: Code,
    sections: [
      {
        kind: 'paragraph',
        title: 'OpenAPI documentation',
        text: 'Every endpoint Eunomia exposes is documented in an interactive Swagger UI at the `/docs` path on the backend. You can read endpoint signatures, see request and response shapes, and even fire test calls from the browser.',
      },
      {
        kind: 'steps',
        title: 'Authenticate against the API',
        items: [
          'Sign in through the web UI to obtain a JWT, or call `POST /api/v1/auth/login` directly with your slug, email, and password.',
          'Copy the `token` field from the response.',
          'On every subsequent API call, include the header `Authorization: Bearer <your-token>`.',
          'The token expires after 24 hours; re-authenticate to refresh.',
        ],
      },
      {
        kind: 'list',
        title: 'Common endpoints',
        items: [
          '`POST /api/v1/auth/register` — create a new user and tenant.',
          '`POST /api/v1/auth/login` — sign in and receive a JWT.',
          '`GET /api/v1/regulations` — paginated list of your tenant\'s regulations.',
          '`POST /api/v1/regulations` — multipart upload of a regulatory document.',
          '`GET /api/v1/regulations/{id}` — full detail including obligations, mappings, brief.',
          '`POST /api/v1/regulations/{id}/brief` — request a brief regeneration.',
          '`POST /api/v1/diff` — compare two regulations at the obligation level.',
          '`GET /api/v1/dashboard` — your tenant\'s KPI summary.',
        ],
      },
      {
        kind: 'tip',
        text: 'For SSE event streaming, point an EventSource at `/api/v1/regulations/{id}/events`. Each event arrives as a JSON payload — the live activity feed in the UI uses this exact endpoint.',
      },
    ],
  },
  {
    id: 'troubleshooting',
    title: 'Troubleshooting',
    description: 'Common issues and how to resolve them.',
    icon: Wrench,
    sections: [
      {
        kind: 'table',
        title: 'Common issues',
        headers: ['Symptom', 'What to try'],
        rows: [
          ['Sign-in form hangs after submit', 'The backend may be restarting. Wait 30 seconds and try again. If it persists, check the platform status page or contact support.'],
          ['Upload succeeds but the saga never starts', 'Refresh the page. The SSE connection may have dropped silently — refreshing reconnects and replays missed events.'],
          ['Brief generation fails with a rate-limit error', 'Click **Try again**. The LLM provider\'s rate-limit window will have rolled over by the time the next attempt fires.'],
          ['Every obligation appears as a gap', 'No internal policies are indexed, or the indexed policies are unrelated to the regulation. Upload more representative policies and re-upload the regulation.'],
          ['Compare regulations dropdown is empty', 'You need at least two regulations with status MAPPED or later. Upload a second regulation and wait for it to finish processing.'],
          ['Live activity feed shows no events', 'The SSE connection may be blocked by a proxy or VPN. Try a different network, or open the regulation detail page in an incognito window.'],
          ['PDF download fails', 'The brief markdown may not be ready yet. Wait for the **Brief ready** badge before downloading.'],
          ['Diff says "Identical scope — no material change"', 'The two regulations have substantively identical obligations. This is expected for minor amendments that only reword existing rules.'],
        ],
      },
      {
        kind: 'paragraph',
        title: 'Still stuck?',
        text: 'If none of these match your symptom, the platform status, current backend version, and your most recent action are all visible in the browser developer tools. Open the Network tab, reproduce the issue, and share a screenshot with support — that gives the fastest path to a fix.',
      },
    ],
  },
]

// ───── Helpers ───────────────────────────────────────────────────

function renderInline(text: string): ReactNode[] {
  // Tiny markdown: **bold** and `code`. Combined into one pass.
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g)
  return parts.map((p, i) => {
    if (p.startsWith('**') && p.endsWith('**')) {
      return <strong key={i}>{p.slice(2, -2)}</strong>
    }
    if (p.startsWith('`') && p.endsWith('`')) {
      return (
        <code key={i} className="rounded bg-slate-100 px-1 py-0.5 font-mono text-[0.85em] text-slate-800">
          {p.slice(1, -1)}
        </code>
      )
    }
    return <span key={i}>{p}</span>
  })
}

// ───── Section renderers ─────────────────────────────────────────

function SectionBlock({ section }: { section: Section }) {
  switch (section.kind) {
    case 'paragraph':
      return (
        <div className="space-y-2">
          <h4 className="font-semibold text-slate-900">{section.title}</h4>
          <p className="text-sm leading-relaxed text-slate-700">{renderInline(section.text)}</p>
        </div>
      )
    case 'steps':
      return (
        <div className="space-y-2">
          <h4 className="font-semibold text-slate-900">{section.title}</h4>
          <ol className="ml-5 list-decimal space-y-1.5 text-sm leading-relaxed text-slate-700 marker:text-slate-400">
            {section.items.map((it, i) => (
              <li key={i}>{renderInline(it)}</li>
            ))}
          </ol>
        </div>
      )
    case 'list':
      return (
        <div className="space-y-2">
          <h4 className="font-semibold text-slate-900">{section.title}</h4>
          <ul className="ml-5 list-disc space-y-1.5 text-sm leading-relaxed text-slate-700 marker:text-slate-400">
            {section.items.map((it, i) => (
              <li key={i}>{renderInline(it)}</li>
            ))}
          </ul>
        </div>
      )
    case 'table':
      return (
        <div className="space-y-2">
          <h4 className="font-semibold text-slate-900">{section.title}</h4>
          <div className="overflow-hidden rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wider text-slate-600">
                <tr>
                  <th className="px-4 py-2.5">{section.headers[0]}</th>
                  <th className="px-4 py-2.5">{section.headers[1]}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {section.rows.map(([a, b], i) => (
                  <tr key={i}>
                    <td className="px-4 py-2.5 align-top font-medium text-slate-800">{renderInline(a)}</td>
                    <td className="px-4 py-2.5 align-top text-slate-700">{renderInline(b)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )
    case 'tip':
      return (
        <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <strong>Tip:</strong> {renderInline(section.text)}
        </div>
      )
    case 'warn':
      return (
        <div className="rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900">
          <strong>Note:</strong> {renderInline(section.text)}
        </div>
      )
  }
}

// ───── Page ──────────────────────────────────────────────────────

export default function HelpDocsPage() {
  const [filter, setFilter] = useState('')
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  const visible = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return TOPICS
    return TOPICS.filter(
      (t) =>
        t.title.toLowerCase().includes(q) ||
        t.description.toLowerCase().includes(q) ||
        t.sections.some((s) => {
          if (s.kind === 'tip' || s.kind === 'warn') return s.text.toLowerCase().includes(q)
          if (s.kind === 'paragraph') {
            return s.title.toLowerCase().includes(q) || s.text.toLowerCase().includes(q)
          }
          if (s.kind === 'steps' || s.kind === 'list') {
            return (
              s.title.toLowerCase().includes(q) ||
              s.items.some((it) => it.toLowerCase().includes(q))
            )
          }
          if (s.kind === 'table') {
            return (
              s.title.toLowerCase().includes(q) ||
              s.rows.some(([a, b]) => a.toLowerCase().includes(q) || b.toLowerCase().includes(q))
            )
          }
          return false
        }),
    )
  }, [filter])

  const toggleExpanded = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const focusTopic = (id: string) => {
    setExpanded((prev) => new Set(prev).add(id))
    // Use a microtask so the accordion is open before scroll.
    setTimeout(() => {
      document.getElementById(`guide-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 50)
  }

  return (
    <div className="mx-auto max-w-7xl p-6 md:p-10 space-y-6">
      <Header />

      <FilterInput value={filter} onChange={setFilter} />

      <TopicsGrid topics={visible} onFocus={focusTopic} />

      <DetailedGuides
        topics={visible}
        expanded={expanded}
        onToggle={toggleExpanded}
      />

      {visible.length === 0 && (
        <div className="card p-8 text-center text-sm text-slate-500">
          No topics match your search. Try a different term.
        </div>
      )}
    </div>
  )
}

function Header() {
  return (
    <header>
      <h1 className="text-2xl font-bold text-slate-900">Help &amp; Documentation</h1>
      <p className="mt-1 max-w-3xl text-sm leading-relaxed text-slate-500">
        Task guides for the Eunomia compliance engine — how the platform works, how to use each
        feature, and what to check when something does not behave as expected. Browse the topic
        cards below or expand a guide for step-by-step detail.
      </p>
    </header>
  )
}

function FilterInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <div className="card flex items-center gap-2 px-4 py-2.5">
      <Search className="h-4 w-4 text-slate-400" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Filter help topics…"
        className="flex-1 bg-transparent text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none"
      />
    </div>
  )
}

function TopicsGrid({
  topics,
  onFocus,
}: {
  topics: HelpTopic[]
  onFocus: (id: string) => void
}) {
  if (topics.length === 0) return null
  return (
    <section className="space-y-3">
      <div className="flex items-baseline gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-700">Topics</h2>
        <span className="text-xs text-slate-400">
          {topics.length} {topics.length === 1 ? 'topic' : 'topics'}
        </span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {topics.map((t) => (
          <TopicCard key={t.id} topic={t} onClick={() => onFocus(t.id)} />
        ))}
      </div>
    </section>
  )
}

function TopicCard({ topic, onClick }: { topic: HelpTopic; onClick: () => void }) {
  const Icon = topic.icon
  return (
    <button
      type="button"
      onClick={onClick}
      className="card group flex flex-col gap-3 p-5 text-left transition-all hover:border-brand-200 hover:shadow-md"
    >
      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600 transition-colors group-hover:bg-brand-100">
        <Icon className="h-5 w-5" />
      </div>
      <div className="space-y-1">
        <h3 className="font-semibold text-slate-900">{topic.title}</h3>
        <p className="text-xs leading-relaxed text-slate-500">{topic.description}</p>
      </div>
    </button>
  )
}

function DetailedGuides({
  topics,
  expanded,
  onToggle,
}: {
  topics: HelpTopic[]
  expanded: Set<string>
  onToggle: (id: string) => void
}) {
  if (topics.length === 0) return null
  return (
    <section className="space-y-3 pt-2">
      <div className="flex items-baseline gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-700">
          Detailed guides
        </h2>
        <span className="text-xs text-slate-400">Click any topic to expand</span>
      </div>
      <div className="space-y-2">
        {topics.map((t) => (
          <GuideAccordion
            key={t.id}
            topic={t}
            isOpen={expanded.has(t.id)}
            onToggle={() => onToggle(t.id)}
          />
        ))}
      </div>
    </section>
  )
}

function GuideAccordion({
  topic,
  isOpen,
  onToggle,
}: {
  topic: HelpTopic
  isOpen: boolean
  onToggle: () => void
}) {
  const Icon = topic.icon
  return (
    <article id={`guide-${topic.id}`} className="card overflow-hidden">
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center gap-3 px-5 py-4 text-left hover:bg-slate-50"
        aria-expanded={isOpen}
      >
        <div
          className={clsx(
            'flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg',
            'bg-brand-50 text-brand-600',
          )}
        >
          <Icon className="h-4 w-4" />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-slate-900">{topic.title}</h3>
          <p className="text-xs text-slate-500">{topic.description}</p>
        </div>
        {isOpen ? (
          <ChevronUp className="h-4 w-4 flex-shrink-0 text-slate-400" />
        ) : (
          <ChevronDown className="h-4 w-4 flex-shrink-0 text-slate-400" />
        )}
      </button>
      {isOpen && (
        <div className="space-y-5 border-t border-slate-100 px-5 py-5 pl-[4.25rem] md:pl-[4.5rem]">
          {topic.sections.map((s, i) => (
            <SectionBlock key={i} section={s} />
          ))}
        </div>
      )}
    </article>
  )
}
