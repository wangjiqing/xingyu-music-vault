import http from './http'

export interface BraveSearchStatus {
  configured: boolean
  enabled: boolean
  searchable: boolean
  mode: string
  message: string
  encryptionAvailable: boolean
  updatedAt: string | null
  lastCheckedAt: string | null
  lastError: string | null
}

export interface BraveSearchResult {
  title: string
  url: string
  domain: string
  description: string
}

export interface BraveSearchResponse {
  query: string
  results: BraveSearchResult[]
}

export async function fetchBraveSearchStatus(): Promise<BraveSearchStatus> {
  const { data } = await http.get('/api/admin/brave-search/status')
  return data
}

export async function saveBraveSearchKey(payload: {
  apiKey: string
  updatedBy?: string
}): Promise<BraveSearchStatus> {
  const { data } = await http.post('/api/admin/brave-search/key', payload)
  return data
}

export async function setBraveSearchEnabled(payload: {
  enabled: boolean
  updatedBy?: string
}): Promise<BraveSearchStatus> {
  const { data } = await http.patch('/api/admin/brave-search/enabled', payload)
  return data
}

export async function testBraveSearchConnection(): Promise<BraveSearchStatus> {
  const { data } = await http.post('/api/admin/brave-search/test', {})
  return data
}

export async function searchBrave(payload: {
  query: string
  count?: number
}): Promise<BraveSearchResponse> {
  const { data } = await http.post('/api/admin/brave-search/search', payload)
  return data
}
