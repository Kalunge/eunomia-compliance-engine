import { Navigate, Route, Routes } from 'react-router-dom'
import LandingPage from '@/pages/LandingPage'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import RegulationsPage from '@/pages/RegulationsPage'
import ProtectedRoute from '@/components/ProtectedRoute'
import AppLayout from '@/components/AppLayout'

export default function App() {
  return (
    <Routes>
      {/* Public surface */}
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Authenticated surface */}
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

      {/* Unknown routes — if signed-in send to app, otherwise the landing */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
