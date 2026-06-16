import { api } from './api'

/**
 * Typed client for POST /api/v1/diff. Mirrors the Kotlin DTOs in
 * src/main/kotlin/io/kalunge/eunomia/diff/RegulationDiffDtos.kt.
 *
 * The endpoint is synchronous and blocks for ~40-60s while the agent
 * embeds and judges each candidate obligation. The 2-minute timeout
 * is a hard cap — the backend itself caps the per-diff time well
 * inside this — so a hang past 2 minutes signals a real failure.
 */

export type DiffVerdict = 'unchanged' | 'modified' | 'added'

export interface DiffSide {
  obligationId: string
  orderIndex: number
  summary: string
  sourceExcerpt: string
}

export interface DiffPair {
  baseline: DiffSide
  candidate: DiffSide
  rationale: string
}

export interface DiffSummary {
  totalBaseline: number
  totalCandidate: number
  unchanged: number
  modified: number
  added: number
  removed: number
  /**
   * (modified + added + removed) / max(totalBaseline, totalCandidate).
   * 0.0 = identical scope, 1.0 = entirely new regulation. UI renders
   * as a percentage.
   */
  materialChangeScore: number
}

export interface RegulationDiff {
  baselineId: string
  baselineTitle: string
  candidateId: string
  candidateTitle: string
  unchanged: DiffPair[]
  modified: DiffPair[]
  added: DiffSide[]
  removed: DiffSide[]
  summary: DiffSummary
}

export async function runDiff(baselineId: string, candidateId: string): Promise<RegulationDiff> {
  const res = await api.post<RegulationDiff>(
    '/v1/diff',
    { baselineId, candidateId },
    { timeout: 120_000 },
  )
  return res.data
}
