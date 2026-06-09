import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

/**
 * Wraps a route element. If the user isn't signed in, redirect to
 * /login and remember where they were going so we can send them back
 * after a successful login.
 */
interface Props {
  children: ReactNode
}

export default function ProtectedRoute({ children }: Props) {
  const isAuthenticated = useAuthStore((s) => Boolean(s.token))
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}
