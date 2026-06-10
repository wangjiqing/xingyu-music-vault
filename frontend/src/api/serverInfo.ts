import http from './http'

export interface AdminServerInfo {
  serviceName: string
  serviceVersion: string
}

export async function fetchAdminServerInfo(): Promise<AdminServerInfo> {
  const { data } = await http.get('/api/admin/server/info')
  return data
}
