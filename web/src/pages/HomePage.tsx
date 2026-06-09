import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

interface HealthResponse {
  status: string
  groups?: string[]
}

// Actuator lives at /actuator/health (not under /api), so this hits the
// backend directly through the Vite dev proxy via a separate axios call.
async function fetchHealth(): Promise<HealthResponse> {
  const res = await axios.get('/api/actuator/health')
  return res.data
}

export default function HomePage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
    refetchInterval: 10_000,
  })

  const status = isError ? 'unreachable' : isLoading ? 'checking...' : data?.status ?? 'unknown'
  const tone =
    status === 'UP'
      ? 'text-emerald-600'
      : status === 'unreachable'
        ? 'text-rose-600'
        : 'text-amber-600'

  return (
    <main className="min-h-screen flex items-center justify-center p-8">
      <section className="max-w-xl text-center">
        <h1 className="text-4xl font-bold text-brand-700">Eunomia</h1>
        <p className="mt-3 text-slate-600">
          AI-powered banking compliance engine. Sprint 0 scaffold.
        </p>
        <div className="mt-8 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-slate-500">Backend health</p>
          <p className={`mt-1 font-mono text-lg ${tone}`}>{status}</p>
        </div>
      </section>
    </main>
  )
}
