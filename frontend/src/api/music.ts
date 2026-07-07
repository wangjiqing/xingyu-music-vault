import http from './http'
import type { PageResponse } from './types'

export interface MusicItem {
  id: number
  title: string
  artist: string
  album: string | null
  albumArtist: string | null
  duration: number | null
  year: number | null
  trackNo: number | null
  genre: string | null
  metadataUpdatedAt: string | null
  lyricStatus: string
  lyricId: number | null
  hasLrc: boolean
  hasSwlrc: boolean
  artworkStatus: string
  artworkId: number | null
  artworkPreviewUrl: string | null
  artworkFileName: string | null
  artworkFileExists: boolean | null
  filePath: string
  fileName: string
  fileExtension: string
  fileSize: number
  lastModifiedTime: string
  createdAt: string
  updatedAt: string
}

export interface MusicScanAccepted {
  accepted: boolean
  scanJobId: number
  message: string
}

export interface MusicListQuery {
  page: number
  size: number
  keyword?: string
  artistKey?: string
  albumKey?: string
  year?: number
  genre?: string
  hasLyrics?: boolean | null
  lyricStatus?: string
  hasArtwork?: boolean | null
  metadata?: string
}

export interface MusicMetadataUpdate {
  title?: string
  artist?: string
  album?: string
  year?: number | null
  trackNo?: number | null
  genre?: string
}

export async function fetchMusicList(query: MusicListQuery): Promise<PageResponse<MusicItem>> {
  const { data } = await http.get('/api/music', { params: query })
  return data
}

export async function triggerMusicScan(payload?: { path?: string }): Promise<MusicScanAccepted> {
  const { data } = await http.post('/api/music/scan', payload || {})
  return data
}

export async function updateMusicMetadata(
  id: number,
  payload: MusicMetadataUpdate,
): Promise<MusicItem> {
  const { data } = await http.put(`/api/music/${id}/metadata`, payload)
  return data
}

export interface MusicFileInfo {
  id: number
  filePath: string
  fileName: string
  fileExtension: string
  fileSize: number
  lastModifiedTime: string
  deletedAt: string | null
  trashPath: string | null
  deleteStatus: string
  createdAt: string
  updatedAt: string
}

export async function fetchMusicFileInfo(id: number): Promise<MusicFileInfo> {
  const { data } = await http.get(`/api/music/${id}/file`)
  return data
}

export async function deleteMusic(id: number): Promise<MusicFileInfo> {
  const { data } = await http.delete(`/api/music/${id}`)
  return data
}

export interface MusicTrashItem {
  id: number
  title: string
  artist: string
  album: string | null
  fileName: string
  originalPath: string
  trashPath: string
  deletedAt: string
  trashFileExists: boolean
  deleteStatus: string
}

export async function fetchTrashList(): Promise<MusicTrashItem[]> {
  const { data } = await http.get('/api/music/trash')
  return data
}

export async function restoreMusic(id: number): Promise<MusicFileInfo> {
  const { data } = await http.post(`/api/music/${id}/restore`)
  return data
}

export async function permanentlyDeleteMusic(id: number): Promise<MusicFileInfo> {
  const { data } = await http.delete(`/api/music/${id}/trash`)
  return data
}

export interface ArtistItem {
  artist: string
  artistKey: string
  trackCount: number
  albumCount: number
  lyricsCount: number
  artworkCount: number
  metadataIncompleteCount: number
}

export interface ArtistListQuery {
  keyword?: string
  page: number
  pageSize: number
  sort?: string
}

export async function fetchArtistList(query: ArtistListQuery): Promise<PageResponse<ArtistItem>> {
  const { data } = await http.get('/api/music/artists', { params: query })
  return data
}

export interface ArtistAlbumItem {
  album: string
  albumKey: string
  year: number | null
  trackCount: number
  lyricsCount: number
  artworkCount: number
  metadataIncompleteCount: number
  coverMusicId: number | null
  sampleMusicId: number | null
}

export interface ArtistDetailResponse {
  artist: string
  artistKey: string
  trackCount: number
  albumCount: number
  lyricsCount: number
  artworkCount: number
  metadataIncompleteCount: number
  albums: ArtistAlbumItem[]
}

export async function fetchArtistDetail(artistKey: string): Promise<ArtistDetailResponse> {
  const { data } = await http.get(`/api/music/artists/${encodeURIComponent(artistKey)}`)
  return data
}

export interface MusicStats {
  total: number
  metadataIncomplete: number
  lyricsReady: number
  artworkReady: number
  trashed: number
}

export async function fetchMusicStats(): Promise<MusicStats> {
  const { data } = await http.get('/api/music/stats')
  return data
}

export interface WorkbenchLyric {
  available: boolean
  lyricId: number | null
  format: string | null
  content: string | null
  updatedAt: string | null
}

export interface WorkbenchWordLyric {
  available: boolean
  format: string | null
  content: string | null
  contentHash: string | null
  updatedAt: string | null
}

export interface WorkbenchArtwork {
  available: boolean
  artworkId: number | null
  mimeType: string | null
  fileName: string | null
  fileSize: number | null
  width: number | null
  height: number | null
  previewUrl: string | null
  updatedAt: string | null
}

export interface OpenApiPreview {
  track: Record<string, unknown>
  lyrics: Record<string, unknown>
  artwork: Record<string, unknown>
  resourceUrls: Record<string, string>
}

export interface MusicWorkbench {
  music: MusicItem
  lyrics: WorkbenchLyric
  wordLyrics: WorkbenchWordLyric
  artwork: WorkbenchArtwork
  openApiPreview: OpenApiPreview
}

export async function fetchMusicWorkbench(id: number): Promise<MusicWorkbench> {
  const { data } = await http.get(`/api/admin/music/${id}/workbench`)
  return data
}

export async function fetchMusicOpenApiPreview(id: number): Promise<OpenApiPreview> {
  const { data } = await http.get(`/api/admin/music/${id}/openapi-preview`)
  return data
}

export function musicAudioUrl(id: number): string {
  return `/api/admin/music/${id}/audio`
}

export interface MusicMetadataBatchUpdate {
  ids: number[]
  artist?: string
  album?: string
  year?: number
  genre?: string
}

export interface MusicMetadataBatchUpdateResponse {
  updated: number
}

export async function batchUpdateMusicMetadata(
  payload: MusicMetadataBatchUpdate,
): Promise<MusicMetadataBatchUpdateResponse> {
  const { data } = await http.put('/api/music/metadata/batch', payload)
  return data
}

export interface AlbumItem {
  album: string
  albumKey: string
  albumArtist: string
  artistKey: string
  year: number | null
  trackCount: number
  lyricsCount: number
  artworkCount: number
  metadataIncompleteCount: number
  coverMusicId: number | null
}

export interface AlbumListQuery {
  keyword?: string
  artistKey?: string
  page: number
  pageSize: number
  sort?: string
}

export async function fetchAlbumList(query: AlbumListQuery): Promise<PageResponse<AlbumItem>> {
  const { data } = await http.get('/api/music/albums', { params: query })
  return data
}

export async function fetchAlbumDetail(albumKey: string, artistKey: string): Promise<AlbumItem> {
  const { data } = await http.get('/api/music/albums/detail', {
    params: { albumKey, artistKey },
  })
  return data
}

export interface MetadataSnapshot {
  title: string | null
  artist: string | null
  album: string | null
  albumArtist: string | null
  year: number | null
  genre: string | null
  trackNumber: number | null
  duration: number | null
}

export interface MetadataCompareSnapshot {
  title: string | null
  artist: string | null
  album: string | null
}

export interface MetadataDiffItem {
  field: string
  databaseValue: unknown
  embeddedValue: unknown
}

export interface MetadataCompareResponse {
  musicId: number
  database: MetadataCompareSnapshot | null
  embedded: MetadataCompareSnapshot | null
  diffs: MetadataDiffItem[]
  status: string
  errorMessage: string | null
}

export interface MetadataSyncRequest {
  mode?: string
  confirm?: boolean
}

export interface MetadataSyncResult {
  musicId: number
  direction: string
  mode: string
  status: string
  beforeDatabase: MetadataSnapshot
  afterDatabase: MetadataSnapshot
  beforeFile: MetadataSnapshot
  afterFile: MetadataSnapshot
  changedFields: string[]
  auditId: number | null
  errorMessage: string | null
}

export interface BatchMetadataCompareRequest {
  musicIds: number[]
}

export interface BatchMetadataSyncRequest {
  musicIds: number[]
  mode?: string
  confirm?: boolean
}

export interface BatchMetadataSyncResponse {
  batchId: string
  total: number
  success: number
  failed: number
  items: MetadataSyncResult[]
}

export async function compareMusicMetadata(id: number): Promise<MetadataCompareResponse> {
  const { data } = await http.get(`/api/music/${id}/metadata/compare`)
  return data
}

export async function applyFileMetadataToDatabase(id: number, request: MetadataSyncRequest): Promise<MetadataSyncResult> {
  const { data } = await http.post(`/api/music/${id}/metadata/apply-file-to-db`, request)
  return data
}

export async function applyDatabaseMetadataToFile(id: number, request: MetadataSyncRequest): Promise<MetadataSyncResult> {
  const { data } = await http.post(`/api/music/${id}/metadata/apply-db-to-file`, request)
  return data
}

export async function batchCompareMusicMetadata(musicIds: number[]): Promise<MetadataCompareResponse[]> {
  const { data } = await http.post('/api/music/metadata/compare', { musicIds } as BatchMetadataCompareRequest)
  return data
}

export async function batchApplyFileMetadataToDatabase(musicIds: number[]): Promise<BatchMetadataSyncResponse> {
  const { data } = await http.post('/api/music/metadata/apply-file-to-db', { musicIds, confirm: true } as BatchMetadataSyncRequest)
  return data
}

export async function batchApplyDatabaseMetadataToFile(musicIds: number[]): Promise<BatchMetadataSyncResponse> {
  const { data } = await http.post('/api/music/metadata/apply-db-to-file', { musicIds, confirm: true } as BatchMetadataSyncRequest)
  return data
}

export interface MetadataAuditListItem {
  id: number
  batchId: string | null
  musicId: number
  musicTitle: string
  filePath: string
  direction: string
  sourceType: string
  targetType: string
  operationType: string
  status: string
  rollbackStatus: string
  changedFields: string[]
  createdAt: string
  errorMessage: string | null
}

export interface MetadataAuditPageResponse {
  items: MetadataAuditListItem[]
  total: number
  page: number
  pageSize: number
}

export interface MetadataAuditDetailResponse {
  id: number
  batchId: string | null
  musicId: number
  musicTitle: string
  filePath: string
  direction: string
  sourceType: string
  targetType: string
  mode: string
  operationType: string
  status: string
  rollbackStatus: string
  beforeDatabase: MetadataSnapshot | null
  afterDatabase: MetadataSnapshot | null
  beforeFile: MetadataSnapshot | null
  afterFile: MetadataSnapshot | null
  changedFields: string[]
  errorMessage: string | null
  rollbackOfAuditId: number | null
  rollbackAuditId: number | null
  createdAt: string
  createdBy: string | null
}

export interface MetadataRollbackRequest {
  confirm: boolean
}

export interface MetadataRollbackResult {
  auditId: number
  rollbackAuditId: number | null
  success: boolean
  message: string | null
  errorMessage: string | null
}

export interface MetadataRollbackPreviewResponse {
  auditId: number
  musicId: number
  rollbackTarget: string
  current: MetadataSnapshot | null
  target: MetadataSnapshot | null
  diffs: MetadataDiffItem[]
  canRollback: boolean
  warnings: string[]
  errorMessage: string | null
}

export interface BatchMetadataRollbackPreviewResponse {
  total: number
  canRollbackCount: number
  cannotRollbackCount: number
  items: MetadataRollbackPreviewResponse[]
}

export interface BatchMetadataRollbackResponse {
  batchId: string
  total: number
  success: number
  failed: number
  items: MetadataRollbackResult[]
}

export interface MetadataAuditQuery {
  musicId?: number
  batchId?: string
  direction?: string
  status?: string
  rollbackStatus?: string
  keyword?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

export async function fetchMetadataAudits(query: MetadataAuditQuery): Promise<MetadataAuditPageResponse> {
  const { data } = await http.get('/api/music/metadata/audits', { params: query })
  return data
}

export async function fetchMetadataAuditDetail(auditId: number): Promise<MetadataAuditDetailResponse> {
  const { data } = await http.get(`/api/music/metadata/audits/${auditId}`)
  return data
}

export async function fetchMetadataRollbackPreview(auditId: number): Promise<MetadataRollbackPreviewResponse> {
  const { data } = await http.get(`/api/music/metadata/audits/${auditId}/rollback-preview`)
  return data
}

export async function rollbackMetadataAudit(auditId: number): Promise<MetadataRollbackResult> {
  const { data } = await http.post(`/api/music/metadata/audits/${auditId}/rollback`, { confirm: true } as MetadataRollbackRequest)
  return data
}

export async function batchFetchMetadataRollbackPreview(auditIds: number[]): Promise<BatchMetadataRollbackPreviewResponse> {
  const { data } = await http.post('/api/music/metadata/audits/rollback-preview', { auditIds })
  return data
}

export async function batchRollbackMetadataAudits(auditIds: number[]): Promise<BatchMetadataRollbackResponse> {
  const { data } = await http.post('/api/music/metadata/audits/rollback', { auditIds, confirm: true })
  return data
}
