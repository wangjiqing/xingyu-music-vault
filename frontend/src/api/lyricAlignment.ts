import http from './http'
import type { PageResponse } from './types'

export type AlignmentArtifactType = 'lrc' | 'swlrc' | 'report' | 'alignment'

export interface LyricAlignmentJob {
  id: string
  songId: number
  lyricId: number | null
  status: string
  reviewStatus: string
  importStatus: string
  workerOutcome: string | null
  audioRelativePath: string | null
  workerAudioPath?: string | null
  trustedLyricsHash: string | null
  trustedLyricsSnapshot?: string | null
  requestSnapshot?: Record<string, unknown> | null
  errorMessage?: string | null
  resultSummary?: Record<string, unknown> | null
  workerStatus?: Record<string, unknown> | null
  alignmentJsonHash?: string | null
  lrcHash?: string | null
  swlrcHash?: string | null
  reportHash?: string | null
  resultAvailable: boolean
  syncMessage?: string | null
  createdBy: string | null
  createdAt: string
  updatedAt: string
  queuedAt?: string | null
  startedAt?: string | null
  completedAt?: string | null
  failedAt?: string | null
  reviewedBy?: string | null
  reviewedAt?: string | null
  reviewNote?: string | null
  importedBy?: string | null
  importedAt?: string | null
  importErrorMessage?: string | null
  importedLyricId?: number | null
}

export type LyricAlignmentJobListItem = LyricAlignmentJob

export interface LyricAlignmentJobQuery {
  page?: number
  size?: number
  status?: string
}

export interface CreateAlignmentJobRequest {
  songId: number
  createdBy?: string
  sections?: unknown
  workerOptions?: Record<string, unknown>
}

export interface ReviewAlignmentJobRequest {
  reviewNote?: string
  reviewedBy?: string
}

export interface ImportAlignmentJobRequest {
  importedBy?: string
}

export interface ImportAlignmentJobResponse {
  jobId: string
  songId: number
  lyricId: number
  importedLyricId: number
  importStatus: string
  lrcHash: string
  swlrcHash: string
  importedAt: string
  importedBy: string
}

export async function createLyricAlignmentJob(
  payload: CreateAlignmentJobRequest,
): Promise<LyricAlignmentJob> {
  const { data } = await http.post('/api/lyric-alignment/jobs', payload)
  return data
}

export async function fetchLyricAlignmentJobs(
  query: LyricAlignmentJobQuery = {},
): Promise<PageResponse<LyricAlignmentJobListItem>> {
  const { data } = await http.get('/api/admin/lyric-alignment/jobs', { params: query })
  return data
}

export async function fetchLyricAlignmentJob(id: string): Promise<LyricAlignmentJob> {
  const { data } = await http.get(`/api/admin/lyric-alignment/jobs/${id}`)
  return data
}

export async function approveLyricAlignmentJob(
  id: string,
  payload: ReviewAlignmentJobRequest,
): Promise<LyricAlignmentJob> {
  const { data } = await http.post(`/api/admin/lyric-alignment/jobs/${id}/approve`, payload)
  return data
}

export async function rejectLyricAlignmentJob(
  id: string,
  payload: ReviewAlignmentJobRequest,
): Promise<LyricAlignmentJob> {
  const { data } = await http.post(`/api/admin/lyric-alignment/jobs/${id}/reject`, payload)
  return data
}

export async function importLyricAlignmentJob(
  id: string,
  payload: ImportAlignmentJobRequest,
): Promise<ImportAlignmentJobResponse> {
  const { data } = await http.post(`/api/admin/lyric-alignment/jobs/${id}/import`, payload)
  return data
}

export async function fetchLyricAlignmentArtifact(
  id: string,
  artifact: AlignmentArtifactType,
): Promise<string> {
  const { data } = await http.get(`/api/admin/lyric-alignment/jobs/${id}/artifacts/${artifact}`, {
    responseType: 'text',
    transformResponse: [(value) => value],
  })
  return typeof data === 'string' ? data : JSON.stringify(data, null, 2)
}
