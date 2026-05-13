import http from './http'
import type { PageResponse, PageQuery } from './types'

export interface TrackFile {
  id: number
  fileName: string
  fileExt: string
  fileSize: number
  filePath: string
  lastModifiedAt: string
  scanJobId: number
}

export interface TrackFileQuery extends PageQuery {
  ext?: string
  keyword?: string
}

export async function fetchTrackFiles(query: TrackFileQuery): Promise<PageResponse<TrackFile>> {
  const { data } = await http.get('/api/track-files', { params: query })
  return data
}
