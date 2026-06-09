import { useAuthStore } from '@/stores/authStore'

/**
 * Placeholder. The full list + upload + detail views land in Chunks 4-5.
 * This stub exists so ProtectedRoute has somewhere meaningful to land
 * the user post-login.
 */
export default function RegulationsPage() {
  const user = useAuthStore((s) => s.user)
  const signOut = useAuthStore((s) => s.signOut)

  return (
    <main className="min-h-screen p-8">
      <div className="mx-auto max-w-3xl">
        <header className="flex items-center justify-between mb-8">
          <h1 className="text-2xl font-semibold text-brand-700">Regulations</h1>
          <div className="text-right text-sm text-slate-500">
            <p className="font-medium text-slate-700">{user?.displayName}</p>
            <button
              onClick={signOut}
              className="mt-1 text-xs text-brand-600 hover:text-brand-700"
            >
              Sign out
            </button>
          </div>
        </header>
        <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center text-slate-500">
          <p className="text-sm">No regulations yet.</p>
          <p className="mt-2 text-xs">
            Upload UI ships in the next chunk. For now you can sanity-check via
            Postman or curl against <code className="font-mono">/api/v1/regulations</code>.
          </p>
        </div>
      </div>
    </main>
  )
}
