import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { login as loginRequest } from '@/lib/auth'
import { apiErrorMessage } from '@/lib/api'
import { useAuthStore } from '@/stores/authStore'
import Logo from '@/components/Logo'

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
      const dest =
        (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/dashboard'
      navigate(dest, { replace: true })
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate({
      email: email.trim().toLowerCase(),
      password,
      tenantSlug: tenantSlug.trim().toLowerCase(),
    })
  }

  return (
    <main className="grid min-h-screen lg:grid-cols-2 bg-white">
      {/* Marketing column — only shown on wide screens */}
      <section className="hidden lg:flex flex-col justify-between bg-gradient-to-br from-brand-700 via-brand-800 to-brand-950 p-10 text-white relative overflow-hidden">
        <div className="absolute inset-0 opacity-20 mix-blend-overlay pointer-events-none"
             style={{
               backgroundImage:
                 'radial-gradient(circle at 20% 20%, rgba(255,255,255,0.4), transparent 40%), radial-gradient(circle at 80% 80%, rgba(255,255,255,0.3), transparent 40%)',
             }}
        />
        <div className="relative inline-flex items-center gap-2.5">
          <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-white/15 backdrop-blur text-xl font-bold">E</span>
          <span className="text-lg font-bold tracking-tight">Eunomia</span>
        </div>
        <div className="relative space-y-4 max-w-md">
          <h2 className="text-4xl font-bold leading-tight">Read every circular. Brief every leader.</h2>
          <p className="text-brand-100/85 leading-relaxed">
            Eunomia reads regulatory documents, extracts the discrete obligations,
            maps them against your internal policy catalog, and produces an impact
            brief in under a minute.
          </p>
          <div className="flex items-center gap-2 text-brand-100/85 text-sm">
            <ShieldCheck className="h-4 w-4" />
            Tenant-isolated. Audit-traced. Bank-grade by design.
          </div>
        </div>
        <p className="relative text-xs text-brand-200/60">
          © 2026 Eunomia. Capstone project — Quantic MSSE.
        </p>
      </section>

      {/* Form column */}
      <section className="flex flex-col justify-center px-6 py-10 sm:px-10 animate-fade-in">
        <div className="mx-auto w-full max-w-sm">
          <div className="lg:hidden mb-8">
            <Logo size="md" />
          </div>

          <h1 className="text-2xl font-bold text-slate-900">Welcome back</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in to your compliance workspace.</p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-4">
            <Field
              label="Bank slug"
              hint="The slug your bank registered with (e.g. demo-bank)"
              value={tenantSlug}
              onChange={setTenantSlug}
              autoComplete="organization"
              placeholder="demo-bank"
              required
            />
            <Field
              label="Email"
              type="email"
              value={email}
              onChange={setEmail}
              autoComplete="email"
              placeholder="you@bank.example"
              required
            />
            <Field
              label="Password"
              type="password"
              value={password}
              onChange={setPassword}
              autoComplete="current-password"
              placeholder="••••••••••••"
              required
            />

            {mutation.isError && (
              <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
                {apiErrorMessage(mutation.error, 'Sign-in failed. Check your credentials.')}
              </div>
            )}

            <button type="submit" disabled={mutation.isPending} className="btn-primary w-full">
              {mutation.isPending ? (
                'Signing in…'
              ) : (
                <>
                  Sign in
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-slate-500">
            New to your tenant?{' '}
            <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
              Create an account
            </Link>
          </p>
        </div>
      </section>
    </main>
  )
}

interface FieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  hint?: string
  placeholder?: string
  autoComplete?: string
  required?: boolean
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  hint,
  placeholder,
  autoComplete,
  required,
}: FieldProps) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={required}
        placeholder={placeholder}
        className="input-field mt-1.5"
      />
      {hint && <span className="mt-1 block text-xs text-slate-400">{hint}</span>}
    </label>
  )
}
