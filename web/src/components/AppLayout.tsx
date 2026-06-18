import { type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  FileText,
  BookOpen,
  Settings,
  LogOut,
  ChevronRight,
  GitCompare,
  HelpCircle,
} from 'lucide-react'
import { clsx } from 'clsx'
import Logo from './Logo'
import { useAuthStore } from '@/stores/authStore'

interface Props {
  children: ReactNode
}

interface NavItem {
  to: string
  label: string
  icon: typeof LayoutDashboard
  disabled?: boolean
}

const PRIMARY_NAV: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/regulations', label: 'Regulations', icon: FileText },
  { to: '/regulations/diff', label: 'Compare regulations', icon: GitCompare },
  { to: '/policies', label: 'Internal policies', icon: BookOpen },
  { to: '/help', label: 'Help & Docs', icon: HelpCircle },
  { to: '/settings', label: 'Settings', icon: Settings, disabled: true },
]

export default function AppLayout({ children }: Props) {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const tenantSlug = useAuthStore((s) => s.tenantSlug)
  const signOut = useAuthStore((s) => s.signOut)

  const handleSignOut = () => {
    signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-slate-50">
      {/* ── Sidebar ─────────────────────────────────────────────── */}
      <aside className="hidden md:flex md:w-64 md:flex-col border-r border-slate-200 bg-white">
        <div className="flex h-16 items-center px-6 border-b border-slate-200">
          <Logo size="md" />
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {PRIMARY_NAV.map((item) => (
            <NavItemLink key={item.to} {...item} />
          ))}
        </nav>

        <div className="border-t border-slate-200 p-3">
          <div className="flex items-center gap-3 rounded-lg px-3 py-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-brand-700 text-sm font-semibold text-white">
              {user?.displayName?.charAt(0).toUpperCase() ?? '?'}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-900">
                {user?.displayName ?? 'Loading…'}
              </p>
              <p className="truncate text-xs text-slate-500">{tenantSlug}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleSignOut}
            className="mt-2 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900 transition-colors focus-ring"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </div>
      </aside>

      {/* ── Main column ─────────────────────────────────────────── */}
      <div className="flex flex-1 flex-col min-w-0">
        {/* Mobile header (sidebar collapses to top bar) */}
        <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-4 md:hidden">
          <Logo size="sm" />
          <button
            type="button"
            onClick={handleSignOut}
            className="text-sm text-slate-600 hover:text-slate-900"
          >
            <LogOut className="h-5 w-5" />
          </button>
        </header>

        <main className="flex-1 overflow-y-auto animate-fade-in">{children}</main>
      </div>
    </div>
  )
}

function NavItemLink({ to, label, icon: Icon, disabled }: NavItem) {
  if (disabled) {
    return (
      <span
        className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-400 cursor-not-allowed"
        title="Coming soon"
      >
        <Icon className="h-4 w-4" />
        {label}
        <span className="ml-auto text-[10px] uppercase tracking-wider text-slate-300">Soon</span>
      </span>
    )
  }
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        clsx(
          'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors focus-ring',
          isActive
            ? 'bg-brand-50 text-brand-700'
            : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900',
        )
      }
    >
      {({ isActive }) => (
        <>
          <Icon className="h-4 w-4" />
          {label}
          {isActive && <ChevronRight className="ml-auto h-3.5 w-3.5 opacity-60" />}
        </>
      )}
    </NavLink>
  )
}
