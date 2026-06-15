import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams, Link } from 'react-router-dom'
import {
  ArrowLeft,
  AlertCircle,
  Clock,
  Calendar,
  HardDrive,
  Layers,
  BookOpen,
  Trash2,
} from 'lucide-react'
import { useState } from 'react'
import { deletePolicy, fetchPolicyDetail } from '@/lib/policies'
import { formatBytes, formatDateTime } from '@/lib/format'
import { apiErrorMessage } from '@/lib/api'
import StatusBadge from '@/components/StatusBadge'
import type { PolicyStatus } from '@/lib/types'

const POLLING_STATUSES: PolicyStatus[] = ['PARSING']

export default function PolicyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['policy', id],
    queryFn: () => fetchPolicyDetail(id!),
    enabled: !!id,
    refetchInterval: (q) =>
      q.state.data && POLLING_STATUSES.includes(q.state.data.status) ? 4000 : false,
  })

  const del = useMutation({
    mutationFn: () => deletePolicy(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policies'] })
      navigate('/policies', { replace: true })
    },
  })

  if (isLoading) {
    return (
      <div className="mx-auto max-w-5xl p-6 md:p-10">
        <div className="card h-40 animate-pulse" />
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="mx-auto max-w-5xl p-6 md:p-10">
        <Link to="/policies" className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700">
          <ArrowLeft className="h-4 w-4" />
          Back to policies
        </Link>
        <div className="card mt-6 flex items-start gap-4 p-6 border-rose-200 bg-rose-50">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">Could not load policy</h3>
            <p className="mt-1 text-sm text-rose-700">{apiErrorMessage(error)}</p>
          </div>
        </div>
      </div>
    )
  }

  const isParsing = data.status === 'PARSING'

  return (
    <div className="mx-auto max-w-5xl p-6 md:p-10 space-y-6">
      <div className="flex items-center justify-between">
        <Link to="/policies" className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700">
          <ArrowLeft className="h-4 w-4" />
          Back to policies
        </Link>
        {confirming ? (
          <span className="inline-flex items-center gap-3">
            <button
              type="button"
              onClick={() => del.mutate()}
              disabled={del.isPending}
              className="text-sm font-medium text-rose-700 hover:text-rose-900 disabled:opacity-50"
            >
              {del.isPending ? 'Deleting…' : 'Confirm delete'}
            </button>
            <button type="button" onClick={() => setConfirming(false)} className="text-sm text-slate-500 hover:text-slate-700">
              Cancel
            </button>
          </span>
        ) : (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm text-rose-600 hover:bg-rose-50"
          >
            <Trash2 className="h-4 w-4" />
            Delete
          </button>
        )}
      </div>

      <header className="space-y-3">
        <div className="flex items-start gap-3">
          <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
            <BookOpen className="h-6 w-6" />
          </div>
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-slate-900 break-words">{data.title}</h1>
            <p className="mt-0.5 text-sm text-slate-500">{data.sourceFilename}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={data.status} />
          {isParsing && <span className="text-xs text-slate-500">Auto-refreshing every 4 seconds…</span>}
        </div>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <MetaCell icon={Layers} label="Chunks" value={data.chunks.length.toString()} />
        <MetaCell icon={HardDrive} label="Size" value={formatBytes(data.sizeBytes)} />
        <MetaCell icon={Calendar} label="Uploaded" value={formatDateTime(data.uploadedAt)} />
        <MetaCell
          icon={Clock}
          label={data.processedAt ? 'Indexed' : 'Processing'}
          value={data.processedAt ? formatDateTime(data.processedAt) : '—'}
        />
      </div>

      {data.lastError && (
        <div className="card border-rose-200 bg-rose-50 p-4 flex items-start gap-3">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">Indexing failed</h3>
            <p className="mt-1 text-sm text-rose-700">{data.lastError}</p>
          </div>
        </div>
      )}

      <section className="space-y-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Chunks ({data.chunks.length})
          </h2>
        </div>

        {data.chunks.length === 0 && !isParsing && (
          <div className="card p-6 text-sm text-slate-500">
            No chunks were produced. The document may have been empty.
          </div>
        )}

        {isParsing && data.chunks.length === 0 && (
          <div className="card p-6 text-sm text-slate-500 flex items-center gap-2">
            <Clock className="h-4 w-4 text-slate-400" />
            Eunomia is chunking and embedding this policy…
          </div>
        )}

        <ul className="space-y-2">
          {data.chunks.map((c) => (
            <li key={c.id} className="card p-4">
              <div className="flex items-baseline justify-between gap-2">
                <span className="text-xs font-mono text-slate-400">#{c.chunkIndex + 1}</span>
                {c.pageStart != null && (
                  <span className="text-xs text-slate-400">
                    pp. {c.pageStart}{c.pageEnd && c.pageEnd !== c.pageStart ? `–${c.pageEnd}` : ''}
                  </span>
                )}
              </div>
              <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700 leading-relaxed">{c.text}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

interface MetaCellProps {
  icon: typeof Clock
  label: string
  value: string
}

function MetaCell({ icon: Icon, label, value }: MetaCellProps) {
  return (
    <div className="card p-4">
      <div className="flex items-center gap-2 text-xs uppercase tracking-wide text-slate-500">
        <Icon className="h-3.5 w-3.5" />
        {label}
      </div>
      <p className="mt-2 truncate text-sm font-medium text-slate-900">{value}</p>
    </div>
  )
}
