import { useQuery } from '@tanstack/react-query'
import { FileText, Upload, AlertCircle, Inbox } from 'lucide-react'
import { Link } from 'react-router-dom'
import { fetchRegulations } from '@/lib/regulations'
import { formatBytes, formatDateTime, formatRelative } from '@/lib/format'
import { apiErrorMessage } from '@/lib/api'
import StatusBadge from '@/components/StatusBadge'
import EmptyState from '@/components/EmptyState'

export default function RegulationsPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['regulations', 0, 20],
    queryFn: () => fetchRegulations(0, 20),
  })

  return (
    <div className="mx-auto max-w-6xl p-6 md:p-10 space-y-6">
      <PageHeader />

      {isLoading && <LoadingSkeleton />}

      {isError && (
        <div className="card flex items-start gap-4 p-6 border-rose-200 bg-rose-50">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">Could not load regulations</h3>
            <p className="mt-1 text-sm text-rose-700">{apiErrorMessage(error)}</p>
          </div>
        </div>
      )}

      {data && data.items.length === 0 && (
        <EmptyState
          icon={Inbox}
          title="No regulations yet"
          description="Upload a CBK circular, a Basel addendum, or any regulatory PDF and Eunomia will extract the obligations, map them against your internal policies, and produce an impact brief."
          action={
            <Link to="/regulations/upload" className="btn-primary">
              <Upload className="h-4 w-4" />
              Upload a regulation
            </Link>
          }
        />
      )}

      {data && data.items.length > 0 && <RegulationsTable items={data.items} />}
    </div>
  )
}

function PageHeader() {
  return (
    <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Regulations</h1>
        <p className="mt-1 text-sm text-slate-500">
          Regulatory documents you've uploaded and the obligations Eunomia has extracted.
        </p>
      </div>
      <Link to="/regulations/upload" className="btn-primary self-start md:self-auto">
        <Upload className="h-4 w-4" />
        Upload regulation
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
  items: import('@/lib/types').RegulationSummary[]
}

function RegulationsTable({ items }: TableProps) {
  return (
    <div className="card overflow-hidden animate-slide-up">
      <table className="min-w-full divide-y divide-slate-200">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Document
            </th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Status
            </th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Obligations
            </th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Uploaded
            </th>
            <th className="hidden lg:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Size
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {items.map((reg) => (
            <tr key={reg.id} className="group transition-colors hover:bg-slate-50/80">
              <td className="px-6 py-4">
                <Link to={`/regulations/${reg.id}`} className="flex items-start gap-3 group/link">
                  <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 group-hover/link:bg-brand-100 transition-colors">
                    <FileText className="h-5 w-5" />
                  </div>
                  <div className="min-w-0">
                    <p className="truncate font-medium text-slate-900 group-hover/link:text-brand-700 transition-colors">
                      {reg.title}
                    </p>
                    <p className="mt-0.5 truncate text-xs text-slate-500">{reg.sourceFilename}</p>
                  </div>
                </Link>
                {/* Mobile inline meta */}
                <div className="mt-2 flex items-center gap-3 md:hidden">
                  <StatusBadge status={reg.status} size="sm" />
                  <span className="text-xs text-slate-500">{formatRelative(reg.uploadedAt)}</span>
                </div>
              </td>
              <td className="hidden md:table-cell px-6 py-4">
                <StatusBadge status={reg.status} />
              </td>
              <td className="hidden md:table-cell px-6 py-4 text-sm text-slate-700">
                {reg.obligationCount === 0 ? (
                  <span className="text-slate-400">—</span>
                ) : (
                  <span>
                    {reg.obligationCount}{' '}
                    {reg.gapCount > 0 && (
                      <span className="ml-1 inline-flex items-center rounded-md bg-gap-50 px-1.5 py-0.5 text-xs text-gap-700">
                        {reg.gapCount} gap{reg.gapCount === 1 ? '' : 's'}
                      </span>
                    )}
                  </span>
                )}
              </td>
              <td
                className="hidden md:table-cell px-6 py-4 text-sm text-slate-500"
                title={formatDateTime(reg.uploadedAt)}
              >
                {formatRelative(reg.uploadedAt)}
              </td>
              <td className="hidden lg:table-cell px-6 py-4 text-sm text-slate-500">
                {formatBytes(reg.sizeBytes)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
