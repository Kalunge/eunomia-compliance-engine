// Shared types mirroring the backend's response DTOs. Kept in sync with
// io.kalunge.eunomia.auth.*Dto and io.kalunge.eunomia.regulation.*Dto.

export type UserRole = 'ADMIN' | 'MEMBER'

export interface User {
  id: string
  email: string
  displayName: string
  role: UserRole
  tenantId: string
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: User
}

export interface ApiError {
  code: string
  message: string
}

export type RegulationStatus =
  | 'PARSING'
  | 'EXTRACTED'
  | 'MAPPED'
  | 'BRIEF_READY'
  | 'FAILED'

export type GapStatus = 'UNKNOWN' | 'MAPPED' | 'GAP'

export interface RegulationSummary {
  id: string
  title: string
  sourceFilename: string
  mimeType: string
  sizeBytes: number
  status: RegulationStatus
  obligationCount: number
  gapCount: number
  uploadedAt: string
  processedAt: string | null
}

export interface MatchedChunk {
  chunkId: string
  policyId: string
  policyTitle: string
  chunkIndex: number
  text: string
  confidence: number
  reasoning: string | null
}

export interface Obligation {
  id: string
  summary: string
  sourceExcerpt: string
  pageStart: number | null
  pageEnd: number | null
  orderIndex: number
  gapStatus: GapStatus
  matchedChunks: MatchedChunk[]
}

export interface RegulationDetail {
  id: string
  title: string
  sourceFilename: string
  mimeType: string
  sizeBytes: number
  status: RegulationStatus
  uploadedAt: string
  processedAt: string | null
  lastError: string | null
  obligations: Obligation[]
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type PolicyStatus = 'PARSING' | 'INDEXED' | 'FAILED'

export interface PolicySummary {
  id: string
  title: string
  sourceFilename: string
  mimeType: string
  sizeBytes: number
  status: PolicyStatus
  chunkCount: number
  uploadedAt: string
  processedAt: string | null
}

export interface PolicyChunkView {
  id: string
  chunkIndex: number
  text: string
  pageStart: number | null
  pageEnd: number | null
}

export interface PolicyDetail {
  id: string
  title: string
  sourceFilename: string
  mimeType: string
  sizeBytes: number
  status: PolicyStatus
  uploadedAt: string
  processedAt: string | null
  lastError: string | null
  chunks: PolicyChunkView[]
}
