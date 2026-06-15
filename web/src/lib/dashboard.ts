import { api } from './api'
import type { DashboardSummary } from './types'

export async function fetchDashboard(): Promise<DashboardSummary> {
  const res = await api.get<DashboardSummary>('/v1/dashboard')
  return res.data
}
