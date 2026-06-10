import { Link } from 'react-router-dom'
import {
  ArrowRight,
  CheckCircle2,
  FileText,
  GitBranch,
  Layers,
  ScanLine,
  Sparkles,
  ShieldCheck,
  Workflow,
  Zap,
} from 'lucide-react'
import Logo from '@/components/Logo'
import { useAuthStore } from '@/stores/authStore'

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white text-slate-900 antialiased overflow-x-hidden">
      <TopNav />
      <Hero />
      <StatsStrip />
      <FeatureGrid />
      <HowItWorks />
      <TechStack />
      <CtaBanner />
      <Footer />
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Top navigation
// ─────────────────────────────────────────────────────────────────────

function TopNav() {
  const isAuthenticated = useAuthStore((s) => Boolean(s.token))
  return (
    <header className="absolute inset-x-0 top-0 z-30">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-5 lg:px-10">
        <Logo />
        <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-slate-600">
          <a href="#features" className="hover:text-slate-900 transition-colors">Features</a>
          <a href="#how" className="hover:text-slate-900 transition-colors">How it works</a>
          <a href="#stack" className="hover:text-slate-900 transition-colors">Architecture</a>
        </nav>
        <div className="flex items-center gap-3">
          {isAuthenticated ? (
            <Link to="/regulations" className="btn-primary text-sm">
              Open app
              <ArrowRight className="h-4 w-4" />
            </Link>
          ) : (
            <>
              <Link to="/login" className="text-sm font-medium text-slate-700 hover:text-slate-900">
                Sign in
              </Link>
              <Link to="/register" className="btn-primary text-sm">
                Get started
                <ArrowRight className="h-4 w-4" />
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Hero
// ─────────────────────────────────────────────────────────────────────

function Hero() {
  const isAuthenticated = useAuthStore((s) => Boolean(s.token))
  return (
    <section className="relative pt-32 pb-20 sm:pt-40 sm:pb-24 lg:pt-48 lg:pb-32 overflow-hidden">
      {/* Decorative background */}
      <div className="absolute inset-0 -z-10">
        <div className="absolute inset-0 bg-gradient-to-b from-brand-50/60 via-white to-white" />
        <div
          className="absolute -top-40 left-1/2 -translate-x-1/2 h-[640px] w-[1100px] rounded-full opacity-30 blur-3xl"
          style={{
            background:
              'radial-gradient(circle at center, rgba(99,102,241,0.4), rgba(67,56,202,0.2) 40%, transparent 70%)',
          }}
        />
        <div className="absolute inset-x-0 top-0 h-[600px] [mask-image:radial-gradient(circle_at_center,black_30%,transparent_70%)]">
          <div className="absolute inset-0 [background:linear-gradient(to_right,#e2e8f0_1px,transparent_1px),linear-gradient(to_bottom,#e2e8f0_1px,transparent_1px)] [background-size:32px_32px] opacity-40" />
        </div>
      </div>

      <div className="mx-auto max-w-7xl px-6 lg:px-10 grid lg:grid-cols-12 gap-12 items-center">
        <div className="lg:col-span-7 animate-fade-in">
          <span className="inline-flex items-center gap-2 rounded-full border border-brand-200 bg-brand-50/80 px-3 py-1 text-xs font-semibold text-brand-700">
            <Sparkles className="h-3.5 w-3.5" />
            Multi-agent compliance, in production
          </span>
          <h1 className="mt-6 text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-slate-900 leading-[1.05]">
            Read every circular.{' '}
            <span className="bg-gradient-to-r from-brand-500 via-brand-700 to-brand-900 bg-clip-text text-transparent">
              Brief every leader.
            </span>
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-relaxed text-slate-600">
            Eunomia ingests regulatory documents, extracts the obligations, maps them
            against your bank's internal policies, flags the gaps, and produces a
            board-ready impact brief in under a minute. Built for compliance teams
            who have outgrown Word and Excel.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            {isAuthenticated ? (
              <Link to="/regulations" className="btn-primary text-base px-5 py-2.5">
                Open app
                <ArrowRight className="h-4 w-4" />
              </Link>
            ) : (
              <>
                <Link to="/register" className="btn-primary text-base px-5 py-2.5">
                  Start free
                  <ArrowRight className="h-4 w-4" />
                </Link>
                <Link to="/login" className="btn-secondary text-base px-5 py-2.5">
                  Sign in
                </Link>
              </>
            )}
          </div>
          <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-slate-500">
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="h-4 w-4 text-emerald-500" />
              Multi-tenant by design
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="h-4 w-4 text-emerald-500" />
              Audit-traced workflow
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="h-4 w-4 text-emerald-500" />
              Citations on every claim
            </span>
          </div>
        </div>

        <div className="lg:col-span-5 animate-slide-up">
          <HeroProductMock />
        </div>
      </div>
    </section>
  )
}

function HeroProductMock() {
  return (
    <div className="relative">
      {/* Floating glow */}
      <div
        className="absolute -inset-8 rounded-3xl opacity-50 blur-2xl -z-10"
        style={{
          background:
            'linear-gradient(135deg, rgba(99,102,241,0.35) 0%, rgba(67,56,202,0.25) 60%, rgba(255,255,255,0) 100%)',
        }}
      />
      <div className="card overflow-hidden ring-1 ring-slate-200">
        <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-2.5">
          <div className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-rose-300" />
            <span className="h-2.5 w-2.5 rounded-full bg-amber-300" />
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-300" />
          </div>
          <span className="font-mono text-[11px] text-slate-400">eunomia / regulations</span>
        </div>
        <div className="bg-white p-5 space-y-3">
          <MockRow
            title="CBK Circular 12/2026"
            sub="Agent KYC Update · 32 pages"
            status="Brief ready"
            statusColor="emerald"
            meta="14 obligations · 2 gaps"
          />
          <MockRow
            title="Basel III Final Amendments"
            sub="Capital adequacy thresholds · 64 pages"
            status="Mapped"
            statusColor="violet"
            meta="27 obligations"
          />
          <MockRow
            title="FCA DP24/3 — Cyber Resilience"
            sub="Discussion paper · 28 pages"
            status="Extracted"
            statusColor="indigo"
            meta="9 obligations"
          />
          <MockRow
            title="EU DORA Implementing Act"
            sub="ICT third-party risk · 41 pages"
            status="Parsing"
            statusColor="slate"
            meta="—"
          />
        </div>
        <div className="border-t border-slate-200 bg-slate-50/50 px-5 py-3 flex items-center justify-between">
          <span className="text-xs text-slate-500">
            Avg. processing time: <span className="font-semibold text-slate-700">52 s</span>
          </span>
          <span className="inline-flex items-center gap-1 text-xs text-emerald-700">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
            </span>
            Live
          </span>
        </div>
      </div>
    </div>
  )
}

interface MockRowProps {
  title: string
  sub: string
  status: string
  statusColor: 'emerald' | 'violet' | 'indigo' | 'slate'
  meta: string
}

function MockRow({ title, sub, status, statusColor, meta }: MockRowProps) {
  const colors: Record<MockRowProps['statusColor'], string> = {
    emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    violet: 'bg-violet-50 text-violet-700 ring-violet-200',
    indigo: 'bg-brand-50 text-brand-700 ring-brand-200',
    slate: 'bg-slate-100 text-slate-600 ring-slate-200',
  }
  return (
    <div className="flex items-center gap-3 rounded-lg border border-slate-100 px-3 py-2.5 hover:bg-slate-50/70 transition-colors">
      <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
        <FileText className="h-4 w-4" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-slate-900">{title}</p>
        <p className="truncate text-xs text-slate-500">{sub}</p>
      </div>
      <div className="hidden sm:block text-xs text-slate-500 whitespace-nowrap">{meta}</div>
      <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${colors[statusColor]}`}>
        {status}
      </span>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Stats strip
// ─────────────────────────────────────────────────────────────────────

function StatsStrip() {
  const stats = [
    { value: '< 60s', label: 'End-to-end on a 40-page circular' },
    { value: '4', label: 'Specialised agents working in sequence' },
    { value: '100%', label: 'Citations grounded in retrieved chunks' },
    { value: '0', label: 'Cross-tenant data leakage by design' },
  ]
  return (
    <section className="border-y border-slate-200 bg-slate-50/40">
      <div className="mx-auto max-w-7xl px-6 lg:px-10 py-10 grid grid-cols-2 lg:grid-cols-4 gap-y-8 gap-x-12">
        {stats.map((s) => (
          <div key={s.label}>
            <p className="font-display text-3xl lg:text-4xl font-extrabold text-slate-900">{s.value}</p>
            <p className="mt-1 text-sm text-slate-500 leading-relaxed">{s.label}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Feature grid
// ─────────────────────────────────────────────────────────────────────

function FeatureGrid() {
  const features = [
    {
      icon: ScanLine,
      title: 'Extract obligations automatically',
      copy: 'Apache Tika parses any PDF, DOCX, or markdown. A LangChain4j Extractor agent identifies discrete obligations with page references — no manual highlight-and-paste.',
    },
    {
      icon: Layers,
      title: 'Map to internal policies via RAG',
      copy: 'Cohere embeddings live in pgvector alongside your relational data. The Mapper agent retrieves the top policy chunks and scores each against the obligation, with citations.',
    },
    {
      icon: GitBranch,
      title: 'Flag gaps before regulators do',
      copy: 'Any obligation with no confident policy match is flagged as a gap, badged in the UI, and counted on the dashboard. Find your blind spots before the auditor does.',
    },
    {
      icon: Workflow,
      title: 'See every agent at work',
      copy: 'Workflow events stream over WebSocket. Watch the Extractor → Mapper → Impact → Summarizer pipeline run live, replay any past run, audit anything.',
    },
    {
      icon: ShieldCheck,
      title: 'Tenant-isolated and auditable',
      copy: 'Every domain row carries a tenant_id, enforced from the JWT through the JPA layer to the database constraint. Every workflow step is an immutable audit event.',
    },
    {
      icon: Zap,
      title: 'Ship the brief in one click',
      copy: 'The Summarizer agent composes a structured impact brief from obligations, mappings, and affected services. Export as Markdown or PDF and forward to leadership.',
    },
  ]
  return (
    <section id="features" className="py-24 lg:py-32">
      <div className="mx-auto max-w-7xl px-6 lg:px-10">
        <SectionHeading
          eyebrow="What it does"
          title="Compliance work, agentically completed"
          sub="A multi-agent workflow that does the legwork — extract, map, analyse, summarise — and leaves the human judgement where it belongs."
        />
        <div className="mt-16 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((f) => (
            <FeatureCard key={f.title} {...f} />
          ))}
        </div>
      </div>
    </section>
  )
}

interface FeatureCardProps {
  icon: typeof Sparkles
  title: string
  copy: string
}

function FeatureCard({ icon: Icon, title, copy }: FeatureCardProps) {
  return (
    <div className="card p-6 hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200">
      <div className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-brand-100 to-brand-50 text-brand-700">
        <Icon className="h-5 w-5" />
      </div>
      <h3 className="mt-5 text-base font-semibold text-slate-900">{title}</h3>
      <p className="mt-2 text-sm leading-relaxed text-slate-600">{copy}</p>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────
// How it works
// ─────────────────────────────────────────────────────────────────────

function HowItWorks() {
  const steps = [
    {
      n: '01',
      title: 'Upload',
      copy: 'Drag and drop the circular, prudential update, or directive. Eunomia validates the MIME type, stores the blob tenant-scoped, and returns immediately.',
    },
    {
      n: '02',
      title: 'Extract',
      copy: 'Apache Tika strips the text. The Extractor agent identifies discrete obligations, each with a page reference and the verbatim source excerpt.',
    },
    {
      n: '03',
      title: 'Map',
      copy: 'The Mapper agent retrieves top policy chunks from your catalog via pgvector, scores relevance, and flags obligations without a confident match.',
    },
    {
      n: '04',
      title: 'Brief',
      copy: 'The Summarizer composes a structured impact brief. Export to Markdown or PDF. Forward to your leadership. Done before lunch.',
    },
  ]
  return (
    <section id="how" className="py-24 lg:py-32 bg-slate-50/50 border-y border-slate-100">
      <div className="mx-auto max-w-7xl px-6 lg:px-10">
        <SectionHeading
          eyebrow="How it works"
          title="A saga of four agents"
          sub="Each step is a persisted workflow event. Retries are bounded. Failures surface, they don't disappear."
        />
        <div className="mt-16 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {steps.map((s) => (
            <div key={s.n} className="relative card p-6">
              <span className="font-display text-5xl font-extrabold text-brand-100/80">{s.n}</span>
              <h3 className="mt-2 text-base font-semibold text-slate-900">{s.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{s.copy}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Tech stack
// ─────────────────────────────────────────────────────────────────────

function TechStack() {
  const stack = [
    'Kotlin 2.0',
    'Spring Boot 3.5',
    'PostgreSQL + pgvector',
    'LangChain4j',
    'Groq Llama 3.3 70B',
    'Cohere embed-english-v3',
    'React 18 + TypeScript',
    'Vite + Tailwind',
    'Testcontainers',
    'Docker on Render',
    'GitHub Actions CI/CD',
    'Flyway migrations',
  ]
  return (
    <section id="stack" className="py-24 lg:py-32">
      <div className="mx-auto max-w-7xl px-6 lg:px-10">
        <SectionHeading
          eyebrow="Built on"
          title="A production-grade JVM stack"
          sub="Engineering choices we can defend: Kotlin coroutines for agent orchestration, pgvector to avoid running a second datastore, Testcontainers for honest integration tests, named patterns documented in the design doc."
        />
        <div className="mt-12 flex flex-wrap justify-center gap-2">
          {stack.map((s) => (
            <span
              key={s}
              className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-1.5 text-sm font-medium text-slate-700 shadow-sm"
            >
              {s}
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}

// ─────────────────────────────────────────────────────────────────────
// CTA banner
// ─────────────────────────────────────────────────────────────────────

function CtaBanner() {
  const isAuthenticated = useAuthStore((s) => Boolean(s.token))
  return (
    <section className="py-20 lg:py-28">
      <div className="mx-auto max-w-5xl px-6 lg:px-10">
        <div
          className="relative overflow-hidden rounded-3xl px-8 py-14 lg:px-16 lg:py-20 text-white shadow-2xl shadow-brand-900/20"
          style={{
            background:
              'linear-gradient(135deg, #4338ca 0%, #312e81 60%, #1e1b4b 100%)',
          }}
        >
          <div
            className="absolute inset-0 opacity-30 mix-blend-overlay pointer-events-none"
            style={{
              backgroundImage:
                'radial-gradient(circle at 20% 30%, rgba(255,255,255,0.5), transparent 50%), radial-gradient(circle at 80% 70%, rgba(255,255,255,0.3), transparent 50%)',
            }}
          />
          <div className="relative flex flex-col lg:flex-row lg:items-center lg:justify-between gap-8">
            <div className="max-w-xl">
              <h2 className="text-3xl lg:text-4xl font-bold leading-tight">
                Stop reading 40-page circulars line by line.
              </h2>
              <p className="mt-4 text-brand-100/90 leading-relaxed">
                Sign up free for the demo tenant and process your first regulation in
                under a minute. No credit card. No sales call.
              </p>
            </div>
            <div className="flex flex-col sm:flex-row gap-3">
              {isAuthenticated ? (
                <Link
                  to="/regulations"
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-white px-6 py-3 font-medium text-brand-800 hover:bg-brand-50 transition-colors"
                >
                  Open app
                  <ArrowRight className="h-4 w-4" />
                </Link>
              ) : (
                <>
                  <Link
                    to="/register"
                    className="inline-flex items-center justify-center gap-2 rounded-lg bg-white px-6 py-3 font-medium text-brand-800 hover:bg-brand-50 transition-colors"
                  >
                    Get started
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                  <Link
                    to="/login"
                    className="inline-flex items-center justify-center rounded-lg border border-white/30 bg-white/10 px-6 py-3 font-medium text-white hover:bg-white/20 transition-colors backdrop-blur"
                  >
                    Sign in
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Footer
// ─────────────────────────────────────────────────────────────────────

function Footer() {
  return (
    <footer className="border-t border-slate-200 bg-white">
      <div className="mx-auto max-w-7xl px-6 lg:px-10 py-12 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
        <div>
          <Logo />
          <p className="mt-3 max-w-md text-sm text-slate-500">
            Eunomia — Greek goddess of good order and lawful conduct. The name
            describes what the product produces: a bank in good order with its
            regulators.
          </p>
        </div>
        <div className="flex items-center gap-6 text-sm text-slate-500">
          <a
            href="https://github.com/Kalunge/eunomia-compliance-engine"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 hover:text-slate-900 transition-colors"
          >
            <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
              <path d="M12 0a12 12 0 00-3.8 23.4c.6.1.8-.3.8-.6v-2.2c-3.3.7-4-1.4-4-1.4-.5-1.4-1.3-1.8-1.3-1.8-1.1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1.1 1.9 2.9 1.3 3.6 1 .1-.8.4-1.3.8-1.6-2.7-.3-5.5-1.3-5.5-5.9 0-1.3.5-2.4 1.2-3.2-.1-.3-.5-1.5.1-3.2 0 0 1-.3 3.3 1.2a11.4 11.4 0 016 0c2.3-1.5 3.3-1.2 3.3-1.2.6 1.7.2 2.9.1 3.2.7.8 1.2 1.9 1.2 3.2 0 4.6-2.8 5.6-5.5 5.9.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A12 12 0 0012 0z" />
            </svg>
            Source
          </a>
          <a href="#features" className="hover:text-slate-900 transition-colors">Features</a>
          <a href="#how" className="hover:text-slate-900 transition-colors">How it works</a>
        </div>
      </div>
      <div className="border-t border-slate-100 py-6">
        <p className="mx-auto max-w-7xl px-6 lg:px-10 text-xs text-slate-400">
          © 2026 Eunomia — Capstone project, Quantic MSSE. Built end-to-end as a
          portfolio piece.
        </p>
      </div>
    </footer>
  )
}

// ─────────────────────────────────────────────────────────────────────
// Section heading helper
// ─────────────────────────────────────────────────────────────────────

interface SectionHeadingProps {
  eyebrow: string
  title: string
  sub: string
}

function SectionHeading({ eyebrow, title, sub }: SectionHeadingProps) {
  return (
    <div className="max-w-3xl mx-auto text-center">
      <span className="text-xs font-bold uppercase tracking-[0.18em] text-brand-600">
        {eyebrow}
      </span>
      <h2 className="mt-3 text-3xl lg:text-4xl font-bold tracking-tight text-slate-900">
        {title}
      </h2>
      <p className="mt-4 text-base lg:text-lg text-slate-600 leading-relaxed">{sub}</p>
    </div>
  )
}
