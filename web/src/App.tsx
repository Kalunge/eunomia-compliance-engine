import { Navigate, Route, Routes } from 'react-router-dom'
import LandingPage from '@/pages/LandingPage'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import RegulationsPage from '@/pages/RegulationsPage'
import UploadRegulationPage from '@/pages/UploadRegulationPage'
import RegulationDetailPage from '@/pages/RegulationDetailPage'
import RegulationDiffPage from '@/pages/RegulationDiffPage'
import PoliciesPage from '@/pages/PoliciesPage'
import UploadPolicyPage from '@/pages/UploadPolicyPage'
import PolicyDetailPage from '@/pages/PolicyDetailPage'
import DashboardPage from '@/pages/DashboardPage'
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
        path="/dashboard"
        element={
          <ProtectedRoute>
            <AppLayout>
              <DashboardPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
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
      <Route
        path="/regulations/upload"
        element={
          <ProtectedRoute>
            <AppLayout>
              <UploadRegulationPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/regulations/diff"
        element={
          <ProtectedRoute>
            <AppLayout>
              <RegulationDiffPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/regulations/:id"
        element={
          <ProtectedRoute>
            <AppLayout>
              <RegulationDetailPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />

      <Route
        path="/policies"
        element={
          <ProtectedRoute>
            <AppLayout>
              <PoliciesPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/policies/upload"
        element={
          <ProtectedRoute>
            <AppLayout>
              <UploadPolicyPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/policies/:id"
        element={
          <ProtectedRoute>
            <AppLayout>
              <PolicyDetailPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
