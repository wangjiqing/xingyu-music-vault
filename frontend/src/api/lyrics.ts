import http from './http'

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
