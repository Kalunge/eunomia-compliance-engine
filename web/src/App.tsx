import { Routes, Route, Navigate } from 'react-router-dom'
import HomePage from '@/pages/HomePage'

// Sprint 0 ships only the placeholder landing. The full route map (login,
// dashboard, regulations, policies, settings) lands in Sprint 1.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
