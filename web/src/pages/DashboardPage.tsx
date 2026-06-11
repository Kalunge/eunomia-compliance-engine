import { useQuery } from '@tanstack/react-query'
import {
  AlertCircle,
  ArrowUpRight,
  FileText,
  AlertTriangle,
  Layers,
  Inbox,
  Upload,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { fetchDashboard } from '@/lib/dashboard'
import { formatRelative } from '@/lib/format'
import { apiErrorMessage } from '@/lib/api'
import StatusBadge from '@/components/StatusBadge'
import EmptyState from '@/components/EmptyState'
import { useAuthStore } from '@/stores/authStore'
import type { DashboardSummary, RecentRegulation } from '@/lib/types'

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
    // Refresh every 30s — picks up async-saga status transitions without
    // hammering the endpoint.
    refetchInterval: 30_000,
  })

  return (
    <div className="mx-auto max-w-6xl p-6 md:p-10 space-y-8">
      <PageHeader displayName={user?.displayName} />

      {isLoading && <LoadingSkeleton />}

      {isError && (
        <div className="card flex items-start gap-4 p-6 border-rose-200 bg-rose-50">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">Could not load dashboard</h3>
            <p className="mt-1 text-sm text-rose-700">{apiErrorMessage(error)}</p>
          </div>
        </div>
      )}

      {data && <DashboardContent data={data} />}
    </div>
  )
}

function PageHeader({ displayName }: { displayName?: string }) {
  const firstName = displayName?.split(/\s+/)[0]
  return (
    <header>
      <h1 className="text-2xl font-bold text-slate-900">
        {firstName ? `Welcome back, ${firstName}` : 'Dashboard'}
      </h1>
      <p className="mt-1 text-sm text-slate-500">
        A live view of your bank's regulatory coverage. Counts update as the saga finishes
        each upload.
      </p>
    </header>
  )
}

function DashboardContent({ data }: { data: DashboardSummary }) {
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-3">
        <KpiCard
          icon={FileText}
          label="Regulations processed"
          value={data.regulationsProcessed}
          tone="brand"
          href="/regulations"
        />
        <KpiCard
          icon={AlertTriangle}
          label="Open gaps"
          value={data.openGaps}
          tone="gap"
          href="/regulations"
          hint={data.openGaps === 0 ? 'No gaps — every obligation has a policy match' : undefined}
        />
        <KpiCard
          icon={Layers}
          label="Affected products"
          value={data.impactedProductCount}
          tone="violet"
          hint="Distinct product lines touched by any obligation"
        />
      </div>

      <section className="space-y-4">
        <header className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Recent activity</h2>
          <Link to="/regulations" className="inline-flex items-center gap-1 text-sm text-brand-600 hover:text-brand-700">
            View all
            <ArrowUpRight className="h-3.5 w-3.5" />
          </Link>
        </header>

        {data.recentActivity.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title="No regulations uploaded yet"
            description="Upload a CBK circular, a Basel paper, or any regulatory document and Eunomia will extract obligations, map them against your internal policies, and flag any coverage gaps."
            action={
              <Link to="/regulations/upload" className="btn-primary">
                <Upload className="h-4 w-4" />
                Upload regulation
              </Link>
            }
          />
        ) : (
          <RecentActivityTable items={data.recentActivity} />
        )}
      </section>
    </>
  )
}

interface KpiCardProps {
  icon: typeof FileText
  label: string
  value: number
  tone: 'brand' | 'gap' | 'violet'
  href?: string
  hint?: string
}

function KpiCard({ icon: Icon, label, value, tone, href, hint }: KpiCardProps) {
  const iconStyles =
    tone === 'brand'
      ? 'bg-brand-50 text-brand-600'
      : tone === 'gap'
        ? 'bg-gap-50 text-gap-600'
        : 'bg-violet-50 text-violet-600'

  const content = (
    <div className="card p-5 transition-shadow hover:shadow-md animate-fade-in">
      <div className="flex items-start justify-between">
        <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${iconStyles}`}>
          <Icon className="h-5 w-5" />
        </div>
        {href && <ArrowUpRight className="h-4 w-4 text-slate-300 group-hover:text-slate-500" />}
      </div>
      <p className="mt-4 text-3xl font-bold text-slate-900 tabular-nums">{value.toLocaleString()}</p>
      <p className="mt-1 text-sm font-medium text-slate-500">{label}</p>
      {hint && <p className="mt-2 text-xs text-slate-400 leading-relaxed">{hint}</p>}
    </div>
  )

  if (href) {
    return (
      <Link to={href} className="group block">
        {content}
      </Link>
    )
  }
  return content
}

function RecentActivityTable({ items }: { items: RecentRegulation[] }) {
  return (
    <div className="card overflow-hidden animate-slide-up">
      <table className="min-w-full divide-y divide-slate-200">
        <thead className="bg-slate-50">
          <tr>
            <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Regulation
            </th>
            <th className="hidden sm:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Status
            </th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Coverage
            </th>
            <th className="hidden md:table-cell px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Uploaded
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {items.map((r) => (
            <tr key={r.id} className="group transition-colors hover:bg-slate-50/80">
              <td className="px-6 py-4">
                <Link to={`/regulations/${r.id}`} className="block">
                  <p className="truncate font-medium text-slate-900 group-hover:text-brand-700 transition-colors">
                    {r.title}
                  </p>
                  <div className="mt-1 flex items-center gap-3 sm:hidden">
                    <StatusBadge status={r.status} size="sm" />
                    <span className="text-xs text-slate-500">{formatRelative(r.uploadedAt)}</span>
                  </div>
                </Link>
              </td>
              <td className="hidden sm:table-cell px-6 py-4">
                <StatusBadge status={r.status} />
              </td>
              <td className="hidden md:table-cell px-6 py-4 text-sm text-slate-700">
                {r.obligationCount === 0 ? (
                  <span className="text-slate-400">—</span>
                ) : (
                  <span>
                    {r.obligationCount - r.gapCount}/{r.obligationCount} mapped
                    {r.gapCount > 0 && (
                      <span className="ml-2 inline-flex items-center rounded-md bg-gap-50 px-1.5 py-0.5 text-xs text-gap-700">
                        {r.gapCount} gap{r.gapCount === 1 ? '' : 's'}
                      </span>
                    )}
                  </span>
                )}
              </td>
              <td className="hidden md:table-cell px-6 py-4 text-sm text-slate-500">
                {formatRelative(r.uploadedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div className="space-y-8 animate-pulse">
      <div className="grid gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="card p-5 h-32">
            <div className="h-10 w-10 rounded-lg bg-slate-200" />
            <div className="mt-4 h-8 w-16 rounded bg-slate-200" />
            <div className="mt-2 h-3 w-24 rounded bg-slate-100" />
          </div>
        ))}
      </div>
      <div className="card p-6 space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3">
            <div className="h-3 w-1/3 rounded bg-slate-200" />
            <div className="h-3 w-20 rounded bg-slate-100" />
          </div>
        ))}
      </div>
    </div>
  )
}
