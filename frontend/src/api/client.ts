export type ApiResponse<T> = {
  code: number
  message: string
  data: T
}

export type PageResult<T> = {
  records: T[]
  total: number
  page: number
  size: number
}

const API_BASE = ''

export function getToken() {
  return localStorage.getItem('hm_badminton_token') || ''
}

export function setToken(token: string) {
  localStorage.setItem('hm_badminton_token', token)
}

export function clearToken() {
  localStorage.removeItem('hm_badminton_token')
}

export async function api<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${API_BASE}${url}`, { ...options, headers })
  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}
