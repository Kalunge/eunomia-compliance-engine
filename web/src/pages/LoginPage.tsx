import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { login as loginRequest } from '@/lib/auth'
import { apiErrorMessage } from '@/lib/api'
import { useAuthStore } from '@/stores/authStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const signIn = useAuthStore((s) => s.signIn)
  const lastTenantSlug = useAuthStore((s) => s.tenantSlug)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [tenantSlug, setTenantSlug] = useState(lastTenantSlug ?? '')

  const mutation = useMutation({
    mutationFn: loginRequest,
    onSuccess: (data) => {
      signIn({ token: data.token, user: data.user, tenantSlug })
      // Redirect to wherever the user was going before they hit the gate.
      const dest = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/regulations'
      navigate(dest, { replace: true })
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate({ email: email.trim().toLowerCase(), password, tenantSlug: tenantSlug.trim().toLowerCase() })
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 p-6">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-brand-700">Eunomia</h1>
          <p className="mt-1 text-sm text-slate-500">AI-powered banking compliance engine</p>
        </div>
        <form
          onSubmit={handleSubmit}
          className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm space-y-4"
        >
          <h2 className="text-lg font-semibold text-slate-800">Sign in</h2>
          <Field
            label="Tenant slug"
            hint="The bank you're signing into (e.g. demo-bank)"
            value={tenantSlug}
            onChange={setTenantSlug}
            autoComplete="organization"
            required
          />
          <Field
            label="Email"
            type="email"
            value={email}
            onChange={setEmail}
            autoComplete="email"
            required
          />
          <Field
            label="Password"
            type="password"
            value={password}
            onChange={setPassword}
            autoComplete="current-password"
            required
          />
          {mutation.isError && (
            <p className="text-sm text-rose-600">
              {apiErrorMessage(mutation.error, 'Sign-in failed. Check your credentials.')}
            </p>
          )}
          <button
            type="submit"
            disabled={mutation.isPending}
            className="w-full rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {mutation.isPending ? 'Signing in…' : 'Sign in'}
          </button>
          <p className="pt-2 text-center text-sm text-slate-500">
            New to your tenant?{' '}
            <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
              Create an account
            </Link>
          </p>
        </form>
      </div>
    </main>
  )
}

interface FieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  hint?: string
  autoComplete?: string
  required?: boolean
}

function Field({ label, value, onChange, type = 'text', hint, autoComplete, required }: FieldProps) {
  return (
    <label className="block text-sm">
      <span className="font-medium text-slate-700">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={required}
        className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
      />
      {hint && <span className="mt-1 block text-xs text-slate-400">{hint}</span>}
    </label>
  )
}
