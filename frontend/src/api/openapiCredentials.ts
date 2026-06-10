import http from './http'

export interface OpenApiCredential {
  id: number
  name: string
  description: string | null
  accessKey: string
  secretFingerprint: string
  scopes: string[]
  enabled: boolean
  createdAt: string
  expiresAt: string | null
  lastUsedAt: string | null
  lastUsedIp: string | null
  lastUsedUserAgent: string | null
}

export interface CreateCredentialRequest {
  name: string
  description?: string
  scopes: string[]
  expiresAt?: string | null
}

export interface CreateCredentialResponse {
  id: number
  name: string
  accessKey: string
  secretKey: string
  secretFingerprint: string
  scopes: string[]
  enabled: boolean
  createdAt: string
  expiresAt: string | null
}

export interface UpdateEnabledRequest {
  enabled: boolean
}

export async function fetchCredentials(): Promise<OpenApiCredential[]> {
  const { data } = await http.get('/api/admin/openapi/credentials')
  return data
}

export async function createCredential(payload: CreateCredentialRequest): Promise<CreateCredentialResponse> {
  const { data } = await http.post('/api/admin/openapi/credentials', payload)
  return data
}

export async function updateCredentialEnabled(id: number, payload: UpdateEnabledRequest): Promise<void> {
  await http.patch(`/api/admin/openapi/credentials/${id}/enabled`, payload)
}

export async function deleteCredential(id: number): Promise<void> {
  await http.delete(`/api/admin/openapi/credentials/${id}`)
}
