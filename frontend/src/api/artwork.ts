import http from './http'
import type { PageResponse } from './types'

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

export async function fetchArtworkList(params: {
  page: number
  size: number
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
