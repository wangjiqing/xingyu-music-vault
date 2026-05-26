import http from './http'

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
  const { data } = await http.get('/api/open/v1/server/info')
  return data
}

export async function fetchSyncState(): Promise<SyncState> {
  const { data } = await http.get('/api/open/v1/sync/state')
  return data
}
