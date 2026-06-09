import { api } from './api'
import type { PageResponse, RegulationDetail, RegulationSummary } from './types'

export async function fetchRegulations(page = 0, size = 20): Promise<PageResponse<RegulationSummary>> {
  const res = await api.get<PageResponse<RegulationSummary>>('/v1/regulations', {
    params: { page, size },
  })
  return res.data
}

export async function fetchRegulationDetail(id: string): Promise<RegulationDetail> {
  const res = await api.get<RegulationDetail>(`/v1/regulations/${id}`)
  return res.data
}

export interface UploadPayload {
  file: File
  title?: string
}

export async function uploadRegulation({ file, title }: UploadPayload): Promise<RegulationSummary> {
  const formData = new FormData()
  formData.append('file', file)
  if (title) formData.append('title', title)
  const res = await api.post<RegulationSummary>('/v1/regulations', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}
