import http from './http'
import type { PageResponse } from './types'

export interface LyricListItem {
  id: number
  title: string | null
  artist: string | null
  album: string | null
  language: string | null
  releaseYear: number | null
  sourceType: string
  sourcePath: string | null
  format: string
  parseStatus: string
  parseMessage: string | null
  bindStatus: string
  boundSongId: number | null
  boundSongTitle: string | null
  boundSongArtist: string | null
  matchType: string | null
  matchScore: number | null
  isPrimary: boolean | null
  createdAt: string
  updatedAt: string
}

export interface BoundSong {
  songId: number
  title: string | null
  artist: string | null
  album: string | null
  fileName: string | null
  matchType: string | null
  matchScore: number | null
  isPrimary: boolean | null
}

export interface LyricDetail {
  id: number
  title: string | null
  artist: string | null
  album: string | null
  language: string | null
  releaseYear: number | null
  sourceType: string
  sourcePath: string | null
  format: string
  content: string | null
  contentHash: string | null
  parseStatus: string
  parseMessage: string | null
  bindStatus: string
  boundSong: BoundSong | null
  boundSongs: BoundSong[]
  createdAt: string
  updatedAt: string
}

export interface SongLyric {
  songId: number
  lyricStatus: string
  lyricId: number | null
  title: string | null
  artist: string | null
  album: string | null
  language: string | null
  releaseYear: number | null
  sourceType: string
  sourcePath: string | null
  format: string
  parseStatus: string
  parseMessage: string | null
  content: string
  createdAt: string
  updatedAt: string
}

export interface LyricScanResponse {
  path: string
  totalFiles: number
  imported: number
  duplicateFiles: number
  matched: number
  unmatched: number
  skippedBindings: number
  failed: number
}

export interface LyricDeleteResponse {
  success: boolean
  message: string
}

export interface LyricListQuery {
  page: number
  size: number
  keyword?: string
  bindStatus?: string
  parseStatus?: string
  sourceType?: string
}

export async function fetchLyricList(query: LyricListQuery): Promise<PageResponse<LyricListItem>> {
  const { data } = await http.get('/api/lyrics', { params: query })
  return data
}

export async function fetchLyricDetail(id: number): Promise<LyricDetail> {
  const { data } = await http.get(`/api/lyrics/${id}`)
  return data
}

export async function fetchSongLyric(songId: number): Promise<SongLyric> {
  const { data } = await http.get(`/api/songs/${songId}/lyrics`)
  return data
}

export async function triggerLyricScan(payload?: {
  path?: string
  overwritePrimary?: boolean
}): Promise<LyricScanResponse> {
  const { data } = await http.post('/api/lyrics/scan', payload || {})
  return data
}

export async function deleteLyricRecord(id: number): Promise<LyricDeleteResponse> {
  const { data } = await http.delete(`/api/lyrics/${id}`)
  return data
}
