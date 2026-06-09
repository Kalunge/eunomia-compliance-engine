import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from '@/pages/LoginPage'
import RegulationsPage from '@/pages/RegulationsPage'
import ProtectedRoute from '@/components/ProtectedRoute'
import AppLayout from '@/components/AppLayout'

/**
 * Routes split into two shells:
 *   - Public routes (e.g. /login, future landing page) render full-bleed.
 *   - Authenticated routes are wrapped in AppLayout (sidebar + top bar).
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/regulations"
        element={
          <ProtectedRoute>
            <AppLayout>
              <RegulationsPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />

      <Route path="/" element={<Navigate to="/regulations" replace />} />
      <Route path="*" element={<Navigate to="/regulations" replace />} />
    </Routes>
  )
}
