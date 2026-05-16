import http from './http'
import type { PageResponse } from './types'

export interface BoundTrack {
  musicId: number
  trackId: number | null
  fileName: string
  filePath: string
  title: string | null
  artist: string | null
}

export interface ArtworkItem {
  id: number
  fileName: string
  fileExt: string
  mimeType: string
  fileSize: number
  width: number | null
  height: number | null
  hash: string
  sourceType: string
  sourcePath: string
  title: string
  description: string | null
  previewUrl: string
  boundCount: number
  boundTracks: BoundTrack[]
  createdAt: string
  updatedAt: string
}

export interface ArtworkScanResponse {
  path: string
  totalFiles: number
  imported: number
  duplicateFiles: number
  autoBound: number
  unmatched: number
  failed: number
}

export interface MusicArtworkBinding {
  musicId: number
  artworkStatus: string
  artworkId: number | null
  artworkPreviewUrl: string | null
  artworkFileName: string | null
}

export async function fetchArtworkList(params: {
  page: number
  size: number
  keyword?: string
}): Promise<PageResponse<ArtworkItem>> {
  const { data } = await http.get('/api/artworks', { params })
  return data
}

export async function fetchArtworkDetail(id: number): Promise<ArtworkItem> {
  const { data } = await http.get(`/api/artworks/${id}`)
  return data
}

export async function triggerArtworkScan(payload?: {
  path?: string
}): Promise<ArtworkScanResponse> {
  const { data } = await http.post('/api/artworks/scan', payload || {})
  return data
}

export async function bindArtworkToMusic(
  musicId: number,
  artworkId: number,
): Promise<MusicArtworkBinding> {
  const { data } = await http.put(`/api/music/${musicId}/artwork`, { artworkId })
  return data
}

export async function unbindArtworkFromMusic(musicId: number): Promise<MusicArtworkBinding> {
  const { data } = await http.delete(`/api/music/${musicId}/artwork`)
  return data
}

export async function importArtworkFile(
  musicId: number,
  file: File,
): Promise<MusicArtworkBinding> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post(`/api/music/${musicId}/artwork/import`, form)
  return data
}
