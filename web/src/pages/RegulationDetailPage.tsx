import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  Calendar,
  CheckCircle2,
  FileText,
  HardDrive,
  Loader2,
  Quote,
  Sparkles,
} from 'lucide-react'
import { fetchRegulationDetail } from '@/lib/regulations'
import { formatBytes, formatDateTime, formatRelative } from '@/lib/format'
import { apiErrorMessage } from '@/lib/api'
import StatusBadge from '@/components/StatusBadge'
import EmptyState from '@/components/EmptyState'
import type {
  GapStatus,
  ImpactedProduct,
  MatchedChunk,
  Obligation,
  RegulationDetail,
  RegulationStatus,
} from '@/lib/types'

/**
 * Polls more aggressively while the workflow is in flight, then settles
 * down once the regulation is in a terminal state.
 */
function pollIntervalFor(status: RegulationStatus | undefined): number | false {
  if (!status) return false
  if (status === 'PARSING' || status === 'EXTRACTED') {
    // Workflow still in flight — Extractor or Mapper still running.
    return 4000
  }
  // MAPPED, BRIEF_READY, FAILED are terminal in Sprint 2 — no point polling.
  return false
}

export default function RegulationDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['regulation', id],
    queryFn: () => fetchRegulationDetail(id),
    enabled: Boolean(id),
    refetchInterval: (query) => pollIntervalFor(query.state.data?.status),
  })

  return (
    <div className="mx-auto max-w-5xl p-6 md:p-10">
      <Link
        to="/regulations"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to regulations
      </Link>

      {isLoading && <LoadingSkeleton />}

      {isError && (
        <div className="card mt-6 flex items-start gap-4 p-6 border-rose-200 bg-rose-50">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">Could not load this regulation</h3>
            <p className="mt-1 text-sm text-rose-700">{apiErrorMessage(error)}</p>
          </div>
        </div>
      )}

      {data && <RegulationContent regulation={data} />}
    </div>
  )
}

function RegulationContent({ regulation }: { regulation: RegulationDetail }) {
  return (
    <>
      <DetailHeader regulation={regulation} />
      <MetaGrid regulation={regulation} />

      {regulation.lastError && (
        <div className="card mt-6 p-6 border-rose-200 bg-rose-50">
          <div className="flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-rose-900">Processing failed</h3>
              <p className="mt-1 text-sm text-rose-700">{regulation.lastError}</p>
            </div>
          </div>
        </div>
      )}

      {regulation.status === 'MAPPED' && regulation.obligations.length > 0 && (
        <CoverageSummary obligations={regulation.obligations} />
      )}

      <section className="mt-10">
        <header className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-slate-900">Extracted obligations</h2>
          <span className="text-xs text-slate-500">
            {regulation.obligations.length}{' '}
            {regulation.obligations.length === 1 ? 'obligation' : 'obligations'}
          </span>
        </header>
        <ObligationsList
          obligations={regulation.obligations}
          status={regulation.status}
        />
      </section>
    </>
  )
}

function DetailHeader({ regulation }: { regulation: RegulationDetail }) {
  const isProcessing =
    regulation.status === 'PARSING' || regulation.status === 'EXTRACTED'

  return (
    <header className="mt-6 mb-8 animate-fade-in">
      <div className="flex items-start gap-4">
        <div className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-600">
          <FileText className="h-7 w-7" />
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-bold text-slate-900 break-words">{regulation.title}</h1>
          <p className="mt-1 text-sm text-slate-500 truncate">{regulation.sourceFilename}</p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <StatusBadge status={regulation.status} />
          {isProcessing && (
            <span className="inline-flex items-center gap-1 text-xs text-slate-500">
              <Loader2 className="h-3 w-3 animate-spin" />
              Auto-refreshing
            </span>
          )}
        </div>
      </div>
    </header>
  )
}

function MetaGrid({ regulation }: { regulation: RegulationDetail }) {
  return (
    <div className="card grid grid-cols-2 md:grid-cols-4 divide-x divide-slate-200">
      <MetaCell
        icon={Calendar}
        label="Uploaded"
        value={formatRelative(regulation.uploadedAt)}
        tooltip={formatDateTime(regulation.uploadedAt)}
      />
      <MetaCell
        icon={CheckCircle2}
        label="Processed"
        value={regulation.processedAt ? formatRelative(regulation.processedAt) : '—'}
        tooltip={regulation.processedAt ? formatDateTime(regulation.processedAt) : 'Not finished yet'}
      />
      <MetaCell icon={HardDrive} label="Size" value={formatBytes(regulation.sizeBytes)} />
      <MetaCell
        icon={FileText}
        label="Format"
        value={mimeLabel(regulation.mimeType)}
        tooltip={regulation.mimeType}
      />
    </div>
  )
}

interface MetaCellProps {
  icon: typeof Calendar
  label: string
  value: string
  tooltip?: string
}

function MetaCell({ icon: Icon, label, value, tooltip }: MetaCellProps) {
  return (
    <div className="px-5 py-4" title={tooltip}>
      <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-slate-400">
        <Icon className="h-3.5 w-3.5" />
        {label}
      </div>
      <p className="mt-1.5 text-sm font-semibold text-slate-900 truncate">{value}</p>
    </div>
  )
}

function ObligationsList({
  obligations,
  status,
}: {
  obligations: Obligation[]
  status: RegulationStatus
}) {
  if (status === 'PARSING') {
    return (
      <EmptyState
        icon={Sparkles}
        title="Eunomia is reading the document"
        description="The Extractor agent will identify each discrete obligation, with page references and source excerpts. This usually takes under a minute."
      />
    )
  }

  if (status === 'FAILED') {
    return (
      <EmptyState
        icon={AlertCircle}
        title="No obligations were extracted"
        description="Processing did not complete. See the error above; you can re-upload the document to try again."
      />
    )
  }

  if (obligations.length === 0) {
    return (
      <EmptyState
        icon={Sparkles}
        title="No obligations extracted yet"
        description="The Extractor agent did not identify any obligations in this document."
      />
    )
  }

  return (
    <ol className="space-y-3 animate-slide-up">
      {obligations.map((o) => (
        <ObligationCard key={o.id} obligation={o} />
      ))}
    </ol>
  )
}

function ObligationCard({ obligation }: { obligation: Obligation }) {
  const hasMatches = obligation.matchedChunks && obligation.matchedChunks.length > 0
  return (
    <li className="card p-5 transition-shadow hover:shadow-md">
      <div className="flex items-start gap-4">
        <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-slate-100 text-xs font-bold text-slate-600">
          {obligation.orderIndex + 1}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-3">
            <p className="text-sm font-medium text-slate-900 leading-relaxed">
              {obligation.summary}
            </p>
            <GapBadge status={obligation.gapStatus} />
          </div>

          {obligation.sourceExcerpt && (
            <blockquote className="mt-3 flex gap-3 rounded-lg bg-slate-50 px-4 py-3 text-xs text-slate-600">
              <Quote className="h-3.5 w-3.5 mt-0.5 flex-shrink-0 text-slate-400" />
              <p className="leading-relaxed italic">{obligation.sourceExcerpt}</p>
            </blockquote>
          )}

          {(obligation.pageStart || obligation.pageEnd) && (
            <p className="mt-2 text-xs text-slate-400">
              {obligation.pageStart === obligation.pageEnd || !obligation.pageEnd
                ? `Page ${obligation.pageStart}`
                : `Pages ${obligation.pageStart}–${obligation.pageEnd}`}
            </p>
          )}

          {obligation.impactedProducts && obligation.impactedProducts.length > 0 && (
            <ImpactedProductChips products={obligation.impactedProducts} />
          )}

          {hasMatches && <MatchedChunksList matches={obligation.matchedChunks} />}

          {!hasMatches && obligation.gapStatus === 'GAP' && (
            <div className="mt-4 flex items-start gap-2 rounded-lg border border-gap-100 bg-gap-50/60 px-3 py-2 text-xs text-gap-700">
              <AlertCircle className="h-3.5 w-3.5 mt-0.5 flex-shrink-0" />
              <span>
                No policy clause in your catalogue satisfies this obligation. Consider
                drafting a new control, or update an existing policy to cover it.
              </span>
            </div>
          )}
        </div>
      </div>
    </li>
  )
}

function ImpactedProductChips({ products }: { products: ImpactedProduct[] }) {
  return (
    <div className="mt-3 flex flex-wrap items-center gap-1.5">
      <span className="text-[11px] font-medium uppercase tracking-wider text-slate-400 mr-1">
        Affects
      </span>
      {products.map((p) => (
        <span
          key={p.id}
          className="inline-flex items-center rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-medium text-violet-700 ring-1 ring-inset ring-violet-100"
        >
          {p.name}
        </span>
      ))}
    </div>
  )
}

function MatchedChunksList({ matches }: { matches: MatchedChunk[] }) {
  return (
    <details className="mt-4 group" open>
      <summary className="flex cursor-pointer items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500 hover:text-slate-700 select-none">
        <BookOpen className="h-3.5 w-3.5 text-emerald-600" />
        <span>
          {matches.length} matching policy {matches.length === 1 ? 'clause' : 'clauses'}
        </span>
      </summary>
      <ul className="mt-3 space-y-2.5">
        {matches.map((m) => (
          <MatchCard key={m.chunkId} match={m} />
        ))}
      </ul>
    </details>
  )
}

function MatchCard({ match }: { match: MatchedChunk }) {
  return (
    <li className="rounded-lg border border-emerald-100 bg-emerald-50/40 px-4 py-3">
      <div className="flex items-baseline justify-between gap-3">
        <p className="truncate text-xs font-semibold text-emerald-900">
          {match.policyTitle}{' '}
          <span className="font-normal text-emerald-700/60">· chunk #{match.chunkIndex + 1}</span>
        </p>
        <ConfidencePill confidence={match.confidence} />
      </div>
      <p className="mt-2 whitespace-pre-wrap text-xs leading-relaxed text-slate-700">
        {match.text}
      </p>
      {match.reasoning && (
        <p className="mt-2 text-[11px] italic text-slate-500">
          <span className="font-medium not-italic text-slate-400">Why: </span>
          {match.reasoning}
        </p>
      )}
    </li>
  )
}

function ConfidencePill({ confidence }: { confidence: number }) {
  const pct = Math.round(confidence * 100)
  const tone =
    pct >= 80
      ? 'bg-emerald-100 text-emerald-800'
      : pct >= 60
        ? 'bg-amber-100 text-amber-800'
        : 'bg-slate-100 text-slate-700'
  return (
    <span
      className={`flex-shrink-0 inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${tone}`}
      title="LLM judge confidence"
    >
      {pct}%
    </span>
  )
}

function CoverageSummary({ obligations }: { obligations: Obligation[] }) {
  const mapped = obligations.filter((o) => o.gapStatus === 'MAPPED').length
  const gaps = obligations.filter((o) => o.gapStatus === 'GAP').length
  const unknown = obligations.length - mapped - gaps
  const coveragePct = obligations.length === 0 ? 0 : Math.round((mapped / obligations.length) * 100)

  return (
    <section className="mt-8 card p-5 animate-fade-in">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-500">
            Policy coverage
          </h3>
          <p className="mt-1 text-2xl font-bold text-slate-900">
            {coveragePct}% <span className="text-sm font-normal text-slate-500">covered</span>
          </p>
        </div>
        <div className="flex gap-3">
          <CoverageStat label="Mapped" value={mapped} tone="emerald" />
          <CoverageStat label="Gaps" value={gaps} tone="gap" />
          {unknown > 0 && <CoverageStat label="Pending" value={unknown} tone="slate" />}
        </div>
      </div>
      <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full bg-emerald-500 transition-all duration-700"
          style={{ width: `${coveragePct}%` }}
        />
      </div>
    </section>
  )
}

function CoverageStat({
  label,
  value,
  tone,
}: {
  label: string
  value: number
  tone: 'emerald' | 'gap' | 'slate'
}) {
  const styles =
    tone === 'emerald'
      ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
      : tone === 'gap'
        ? 'bg-gap-50 text-gap-700 ring-gap-100'
        : 'bg-slate-50 text-slate-700 ring-slate-200'
  return (
    <div
      className={`flex-1 rounded-lg px-4 py-2 ring-1 ring-inset text-center min-w-[80px] ${styles}`}
    >
      <p className="text-xl font-bold leading-tight">{value}</p>
      <p className="text-[11px] font-medium uppercase tracking-wider">{label}</p>
    </div>
  )
}

function GapBadge({ status }: { status: GapStatus }) {
  if (status === 'UNKNOWN') return null
  const styles =
    status === 'MAPPED'
      ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
      : 'bg-gap-50 text-gap-700 ring-gap-100'
  const label = status === 'MAPPED' ? 'Mapped' : 'Gap'
  return (
    <span
      className={`flex-shrink-0 inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${styles}`}
    >
      {label}
    </span>
  )
}

function LoadingSkeleton() {
  return (
    <div className="mt-6 space-y-4 animate-pulse">
      <div className="h-8 w-2/3 rounded bg-slate-200" />
      <div className="h-4 w-1/3 rounded bg-slate-100" />
      <div className="grid grid-cols-2 md:grid-cols-4 gap-px bg-slate-200 rounded-xl overflow-hidden">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="bg-white p-5 space-y-2">
            <div className="h-3 w-12 rounded bg-slate-200" />
            <div className="h-4 w-20 rounded bg-slate-200" />
          </div>
        ))}
      </div>
      <div className="space-y-3 pt-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="card p-5">
            <div className="space-y-2">
              <div className="h-4 w-3/4 rounded bg-slate-200" />
              <div className="h-3 w-1/2 rounded bg-slate-100" />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function mimeLabel(mime: string): string {
  switch (mime) {
    case 'application/pdf':
      return 'PDF'
    case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
      return 'DOCX'
    case 'application/msword':
      return 'DOC'
    case 'text/plain':
      return 'Plain text'
    case 'text/markdown':
    case 'text/x-markdown':
      return 'Markdown'
    default:
      return mime.split('/').pop() ?? mime
  }
}
