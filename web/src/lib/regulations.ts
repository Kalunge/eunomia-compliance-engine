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

export async function regenerateBrief(id: string): Promise<void> {
  await api.post(`/v1/regulations/${id}/brief`)
}

/**
 * Trigger a browser download of the rendered PDF. We can't just use a
 * plain anchor href because the endpoint needs the Authorization
 * header; instead we fetch with auth, build a Blob URL, and click an
 * anchor pointing at it.
 */
export async function downloadBriefPdf(id: string, filename = 'impact-brief.pdf'): Promise<void> {
  const res = await api.get(`/v1/regulations/${id}/brief.pdf`, { responseType: 'blob' })
  const blob = new Blob([res.data as BlobPart], { type: 'application/pdf' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
