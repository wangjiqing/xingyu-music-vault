import http from './http'

export interface TrackFile {
  id: number
  path: string
  filename: string
  size: number
  modifiedAt: string
}

export async function fetchTrackFiles(): Promise<TrackFile[]> {
  const { data } = await http.get('/api/track-files')
  return data
}
