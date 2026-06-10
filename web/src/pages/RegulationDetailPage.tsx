import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  AlertCircle,
  ArrowLeft,
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
import type { GapStatus, Obligation, RegulationDetail, RegulationStatus } from '@/lib/types'

/**
 * Polls more aggressively while the workflow is in flight, then settles
 * down once the regulation is in a terminal state.
 */
function pollIntervalFor(status: RegulationStatus | undefined): number | false {
  if (!status) return false
  if (status === 'PARSING' || status === 'EXTRACTED' || status === 'MAPPED') {
    return 4000
  }
  // BRIEF_READY and FAILED are terminal — no point polling.
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
    regulation.status === 'PARSING' ||
    regulation.status === 'EXTRACTED' ||
    regulation.status === 'MAPPED'

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
        </div>
      </div>
    </li>
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
