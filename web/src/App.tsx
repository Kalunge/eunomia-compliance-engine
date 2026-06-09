import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from '@/pages/LoginPage'
import RegulationsPage from '@/pages/RegulationsPage'
import ProtectedRoute from '@/components/ProtectedRoute'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/regulations"
        element={
          <ProtectedRoute>
            <RegulationsPage />
          </ProtectedRoute>
        }
      />
      {/* Default route redirects to /regulations; ProtectedRoute kicks
          unauthenticated users out to /login. */}
      <Route path="/" element={<Navigate to="/regulations" replace />} />
      <Route path="*" element={<Navigate to="/regulations" replace />} />
    </Routes>
  )
}
