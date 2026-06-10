import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Upload, AlertCircle, Inbox, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useState } from 'react'
import { deletePolicy, fetchPolicies } from '@/lib/policies'
import { formatBytes, formatDateTime, formatRelative } from '@/lib/format'
import { apiErrorMessage } from '@/lib/api'
import StatusBadge from '@/components/StatusBadge'
import EmptyState from '@/components/EmptyState'
import type { PolicySummary } from '@/lib/types'

export default function PoliciesPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['policies', 0, 20],
    queryFn: () => fetchPolicies(0, 20),
  })

  return (
    <div className="mx-auto max-w-6xl p-6 md:p-10 space-y-6">
      <PageHeader />

      {isLoading && <LoadingSkeleton />}

      {isError && (
        <div className="card flex items-start gap-4 p-6 border-rose-200 bg-rose-50">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">Could not load policies</h3>
            <p className="mt-1 text-sm text-rose-700">{apiErrorMessage(error)}</p>
          </div>
        </div>
      )}

      {data && data.items.length === 0 && (
        <EmptyState
          icon={Inbox}
          title="No internal policies yet"
          description="Upload your bank's internal policy documents so Eunomia can map them against regulatory obligations and surface coverage gaps."
          action={
            <Link to="/policies/upload" className="btn-primary">
              <Upload className="h-4 w-4" />
              Upload a policy
            </Link>
          }
        />
      )}

      {data && data.items.length > 0 && <PoliciesTable items={data.items} />}
    </div>
  )
}

function PageHeader() {
  return (
    <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Internal policies</h1>
        <p className="mt-1 text-sm text-slate-500">
          Policies you've indexed for matching against regulatory obligations.
        </p>
      </div>
      <Link to="/policies/upload" className="btn-primary self-start md:self-auto">
        <Upload className="h-4 w-4" />
        Upload policy
      </Link>
    </header>
  )
}

function LoadingSkeleton() {
  return (
    <div className="card overflow-hidden">
      <div className="divide-y divide-slate-100">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 px-6 py-4 animate-pulse">
            <div className="h-10 w-10 rounded-lg bg-slate-200" />
            <div className="flex-1 space-y-2">
              <div className="h-3 w-1/3 rounded bg-slate-200" />
              <div className="h-3 w-1/4 rounded bg-slate-100" />
            </div>
            <div className="h-6 w-20 rounded-full bg-slate-200" />
          </div>
        ))}
      </div>
    </div>
  )
}

interface TableProps {
  items: PolicySummary[]
}

function PoliciesTable({ items }: TableProps) {
  return (
    <div className="card overflow-hidden animate-slide-up">
      <table className="min-w-full divide-y divide-slate-200">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Policy</th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Status</th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Chunks</th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Uploaded</th>
            <th className="hidden lg:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Size</th>
            <th className="px-6 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {items.map((p) => (
            <PolicyRow key={p.id} policy={p} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PolicyRow({ policy }: { policy: PolicySummary }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)

  const del = useMutation({
    mutationFn: () => deletePolicy(policy.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['policies'] }),
  })

  return (
    <tr className="group transition-colors hover:bg-slate-50/80">
      <td className="px-6 py-4">
        <Link to={`/policies/${policy.id}`} className="flex items-start gap-3 group/link">
          <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 group-hover/link:bg-brand-100 transition-colors">
            <BookOpen className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-900 group-hover/link:text-brand-700 transition-colors">
              {policy.title}
            </p>
            <p className="mt-0.5 truncate text-xs text-slate-500">{policy.sourceFilename}</p>
          </div>
        </Link>
        <div className="mt-2 flex items-center gap-3 md:hidden">
          <StatusBadge status={policy.status} size="sm" />
          <span className="text-xs text-slate-500">{formatRelative(policy.uploadedAt)}</span>
        </div>
      </td>
      <td className="hidden md:table-cell px-6 py-4">
        <StatusBadge status={policy.status} />
      </td>
      <td className="hidden md:table-cell px-6 py-4 text-sm text-slate-700">
        {policy.chunkCount === 0 ? (
          <span className="text-slate-400">—</span>
        ) : (
          policy.chunkCount
        )}
      </td>
      <td className="hidden md:table-cell px-6 py-4 text-sm text-slate-500" title={formatDateTime(policy.uploadedAt)}>
        {formatRelative(policy.uploadedAt)}
      </td>
      <td className="hidden lg:table-cell px-6 py-4 text-sm text-slate-500">{formatBytes(policy.sizeBytes)}</td>
      <td className="px-6 py-4 text-right">
        {confirming ? (
          <span className="inline-flex items-center gap-2">
            <button
              type="button"
              onClick={() => del.mutate()}
              disabled={del.isPending}
              className="text-xs font-medium text-rose-700 hover:text-rose-900 disabled:opacity-50"
            >
              {del.isPending ? 'Deleting…' : 'Confirm delete'}
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              className="text-xs text-slate-500 hover:text-slate-700"
            >
              Cancel
            </button>
          </span>
        ) : (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="inline-flex items-center gap-1 rounded-md p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600 transition-colors"
            aria-label="Delete policy"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        )}
      </td>
    </tr>
  )
}
