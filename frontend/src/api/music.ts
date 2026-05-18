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
