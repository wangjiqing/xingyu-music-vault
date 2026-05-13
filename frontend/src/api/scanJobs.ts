import http from './http'

export interface ScanJob {
  id: number
  name: string
  path: string
  status: string
  createdAt: string
  updatedAt: string
}

export async function fetchScanJobs(): Promise<ScanJob[]> {
  const { data } = await http.get('/api/scan-jobs')
  return data
}

export async function createScanJob(job: { name: string; path: string }): Promise<ScanJob> {
  const { data } = await http.post('/api/scan-jobs', job)
  return data
}

export async function runScanJob(id: number): Promise<void> {
  await http.post(`/api/scan-jobs/${id}/run`)
}
