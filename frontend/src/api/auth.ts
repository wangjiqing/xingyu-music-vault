import http from './http'

export interface SetupStatusResponse {
  initialized: boolean
}

export interface SetupRequest {
  username: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface AdminUserResponse {
  id: number
  username: string
  role: string
}

export async function getSetupStatus(): Promise<SetupStatusResponse> {
  const res = await http.get<SetupStatusResponse>('/api/admin/auth/setup-status')
  return res.data
}

export async function setupAdminAccount(payload: SetupRequest): Promise<void> {
  await http.post('/api/admin/auth/setup', payload)
}

export async function login(payload: LoginRequest): Promise<void> {
  await http.post('/api/admin/auth/login', payload)
}

export async function logout(): Promise<void> {
  await http.post('/api/admin/auth/logout')
}

export async function getCurrentUser(): Promise<AdminUserResponse> {
  const res = await http.get<AdminUserResponse>('/api/admin/auth/me')
  return res.data
}
