import http from './http'
import type { PageResponse } from './types'

export type AlignmentArtifactType = 'lrc' | 'swlrc' | 'report' | 'alignment'
export type DraftArtifactType = 'cleaned' | 'raw' | 'segments' | 'report'

export interface LyricAlignmentJob {
  id: string
  taskType: string
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
  workerSignals?: WorkerSignals | null
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
  draftStatus?: string | null
  confirmedTrustedLyricsId?: number | null
}

export interface WorkerSignals {
  jobDirectoryAvailable: boolean
  ready: boolean
  running: boolean
  succeeded: boolean
  needsReview: boolean
  failed: boolean
  abandoned: boolean
  statusJsonAvailable: boolean
  resultDirectoryAvailable: boolean
  stderrLogAvailable: boolean
  stageMessage: string
}

export type LyricAlignmentJobListItem = LyricAlignmentJob

export interface LyricAlignmentJobQuery {
  page?: number
  size?: number
  status?: string
}

export interface CreateAlignmentJobRequest {
  songId: number
  sourceLyricsAssetId?: number | null
  createdBy?: string
  sections?: unknown
  workerOptions?: Record<string, unknown>
}

export interface CreateLyricDraftJobRequest {
  language?: string
  asrModel?: string
  skipSeparation?: boolean
  vadFilter?: boolean
  conditionOnPreviousText?: boolean
  keepSuspectedMetadata?: boolean
  retainIntermediate?: boolean
  createdBy?: string
}

export interface CreateManualLyricDraftRequest {
  text: string
  createdBy?: string
}

export interface LyricDraftDefaultOptions {
  language: string
  asrModel: string
  skipSeparation: boolean
  vadFilter: boolean
  conditionOnPreviousText: boolean
  keepSuspectedMetadata: boolean
  retainIntermediate: boolean
}

export interface LyricDraftTrustedAsset {
  id: number
  sourceType: string
  contentHash: string
  confirmedAt: string | null
  confirmedBy: string | null
}

export interface LyricDraft {
  jobId: string
  musicId: number
  executionStatus: string
  draftStatus: string
  originalText: string
  originalTextHash: string
  editableText: string
  editableTextHash: string
  reportSummary?: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
  editedBy?: string | null
  editedAt?: string | null
  confirmedBy?: string | null
  confirmedAt?: string | null
  confirmedTrustedLyricsId?: number | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  rejectNote?: string | null
  errorMessage?: string | null
  sourceType?: string | null
  sourceMetadata?: Record<string, unknown> | null
  sources?: LyricDraftSource[]
}

export interface LyricDraftSource {
  id: number
  provider: string
  query: string
  title: string
  url: string
  domain: string
  selectedBy: string
  selectedAt: string
}

export interface LyricDraftSourceRequest {
  provider: string
  query: string
  title: string
  url: string
  domain: string
  selectedBy?: string
}

export interface MusicLyricDraftContext {
  musicId: number
  defaultOptions: LyricDraftDefaultOptions
  latestJob: LyricAlignmentJob | null
  draft: LyricDraft | null
  trustedLyricsAsset: LyricDraftTrustedAsset | null
}

export interface UpdateLyricDraftRequest {
  text: string
  editedBy?: string
}

export interface ConfirmLyricDraftRequest {
  note?: string
  confirmedBy?: string
}

export interface ConfirmLyricDraftResponse {
  jobId: string
  draftId: number
  trustedLyricsId: number
  draftStatus: string
  editableTextHash: string
  confirmedAt: string
  confirmedBy: string
}

export interface RejectLyricDraftRequest {
  rejectNote: string
  rejectedBy?: string
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

export async function fetchMusicLyricDraftContext(musicId: number): Promise<MusicLyricDraftContext> {
  const { data } = await http.get(`/api/admin/music/${musicId}/lyric-draft-jobs/latest`)
  return data
}

export async function createLyricDraftJob(
  musicId: number,
  payload: CreateLyricDraftJobRequest,
): Promise<LyricAlignmentJob> {
  const { data } = await http.post(`/api/admin/music/${musicId}/lyric-draft-jobs`, payload)
  return data
}

export async function createManualLyricDraft(
  musicId: number,
  payload: CreateManualLyricDraftRequest,
): Promise<LyricDraft> {
  const { data } = await http.post(`/api/admin/music/${musicId}/lyric-drafts/manual`, payload)
  return data
}

export async function fetchLyricDraft(jobId: string): Promise<LyricDraft> {
  const { data } = await http.get(`/api/admin/lyric-draft-jobs/${jobId}/draft`)
  return data
}

export async function updateLyricDraft(
  jobId: string,
  payload: UpdateLyricDraftRequest,
): Promise<LyricDraft> {
  const { data } = await http.put(`/api/admin/lyric-draft-jobs/${jobId}/draft`, payload)
  return data
}

export async function confirmLyricDraft(
  jobId: string,
  payload: ConfirmLyricDraftRequest,
): Promise<ConfirmLyricDraftResponse> {
  const { data } = await http.post(`/api/admin/lyric-draft-jobs/${jobId}/confirm`, payload)
  return data
}

export async function rejectLyricDraft(
  jobId: string,
  payload: RejectLyricDraftRequest,
): Promise<LyricDraft> {
  const { data } = await http.post(`/api/admin/lyric-draft-jobs/${jobId}/reject`, payload)
  return data
}

export async function addLyricDraftSource(
  jobId: string,
  payload: LyricDraftSourceRequest,
): Promise<LyricDraftSource> {
  const { data } = await http.post(`/api/admin/lyric-draft-jobs/${jobId}/sources`, payload)
  return data
}

export async function fetchLyricDraftArtifact(
  jobId: string,
  artifact: DraftArtifactType,
): Promise<string> {
  const { data } = await http.get(`/api/admin/lyric-draft-jobs/${jobId}/artifacts/${artifact}`, {
    responseType: 'text',
    transformResponse: [(value) => value],
  })
  return typeof data === 'string' ? data : JSON.stringify(data, null, 2)
}
