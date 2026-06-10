import { api } from './api'
import type { PageResponse, PolicyDetail, PolicySummary } from './types'

export async function fetchPolicies(page = 0, size = 20): Promise<PageResponse<PolicySummary>> {
  const res = await api.get<PageResponse<PolicySummary>>('/v1/policies', {
    params: { page, size },
  })
  return res.data
}

export async function fetchPolicyDetail(id: string): Promise<PolicyDetail> {
  const res = await api.get<PolicyDetail>(`/v1/policies/${id}`)
  return res.data
}

export interface UploadPolicyPayload {
  file: File
  title?: string
}

export async function uploadPolicy({ file, title }: UploadPolicyPayload): Promise<PolicySummary> {
  const formData = new FormData()
  formData.append('file', file)
  if (title) formData.append('title', title)
  const res = await api.post<PolicySummary>('/v1/policies', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}

export async function deletePolicy(id: string): Promise<void> {
  await api.delete(`/v1/policies/${id}`)
}
