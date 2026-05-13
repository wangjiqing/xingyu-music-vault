import http from './http'
import type { PageResponse, PageQuery } from './types'

export interface ScanJob {
  id: number
  jobType: string
  musicDirs: string[]
  status: string
  totalFiles: number
  scannedFiles: number
  newFiles: number
  updatedFiles: number
  skippedFiles: number
  errorFiles: number
  errorMessage: string
  createdAt: string
  updatedAt: string
}

export interface ScanJobQuery extends PageQuery {
  status?: string
}

export async function fetchScanJobs(query: ScanJobQuery): Promise<PageResponse<ScanJob>> {
  const { data } = await http.get('/api/scan-jobs', { params: query })
  return data
}

export async function createScanJob(job: {
  jobType: string
  musicDirs: string[]
}): Promise<ScanJob> {
  const { data } = await http.post('/api/scan-jobs', job)
  return data
}

export async function runScanJob(id: number): Promise<void> {
  await http.post(`/api/scan-jobs/${id}/run`)
}
