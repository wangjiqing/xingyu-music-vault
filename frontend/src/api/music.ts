import http from './http'
import type { PageResponse } from './types'

export interface MusicItem {
  id: number
  title: string
  artist: string
  album: string | null
  albumArtist: string | null
  duration: number | null
  lyricStatus: string
  lyricId: number | null
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
}

export async function fetchMusicList(query: MusicListQuery): Promise<PageResponse<MusicItem>> {
  const { data } = await http.get('/api/music', { params: query })
  return data
}

export async function triggerMusicScan(payload?: { path?: string }): Promise<MusicScanAccepted> {
  const { data } = await http.post('/api/music/scan', payload || {})
  return data
}
