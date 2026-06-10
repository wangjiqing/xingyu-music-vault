import axios from 'axios'

const openApiHttp = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

export interface ServerInfo {
  serviceName: string
  serviceVersion: string
  apiVersion: string
  readOnly: boolean
  features: Record<string, boolean>
}

export interface SyncState {
  trackCount: number
  artistCount: number
  albumCount: number
  lyricsCount: number
  artworkCount: number
  lastUpdatedAt: string
}

export async function fetchServerInfo(): Promise<ServerInfo> {
  const { data } = await openApiHttp.get('/api/open/v1/server/info')
  return data
}

export async function fetchSyncState(): Promise<SyncState> {
  const { data } = await openApiHttp.get('/api/open/v1/sync/state')
  return data
}
