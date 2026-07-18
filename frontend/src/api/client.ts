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

export type StreamEventHandler = (event: string, data: unknown) => void

// 业务代码只传后端资源路径；统一在这里加上网关前缀 /api。
const API_BASE = '/api'

// Token 只保存在浏览器本地；每次请求由 api() 统一加入 Authorization 请求头。
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
  // 1. JSON 请求统一设置 Content-Type；上传 FormData 时必须让浏览器自动生成 multipart boundary。
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  // 2. 登录后自动携带 Bearer Token，页面函数不用重复拼接认证头。
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  // 3. 开发环境由 Vite、生产环境由 Nginx 转发 /api，所以浏览器始终请求同源相对路径。
  const response = await fetch(`${API_BASE}${url}`, { ...options, headers })
  const raw = await response.text()
  let body: ApiResponse<T>
  try {
    body = JSON.parse(raw) as ApiResponse<T>
  } catch {
    // 反向代理、网关或 CORS 拒绝有时返回纯文本，避免把底层错误伪装成 JSON 解析异常。
    throw new Error(raw || `请求失败（HTTP ${response.status}）`)
  }
  // 4. 后端统一返回 ApiResponse；业务 code 非 0 时转成异常，交给页面提示层处理。
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

/**
 * 使用 fetch 读取服务端 SSE 流。
 * EventSource 只支持 GET，助手需要 POST JSON，所以这里手动解析 text/event-stream。
 */
export async function streamApi(
  url: string,
  options: RequestInit,
  onEvent: StreamEventHandler
): Promise<void> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'text/event-stream')
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE}${url}`, { ...options, headers })
  if (!response.ok) {
    throw new Error(await responseError(response))
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) {
      dispatchSseFrame(frame, onEvent)
    }
    if (done) break
  }
  if (buffer.trim()) {
    dispatchSseFrame(buffer, onEvent)
  }
}

function dispatchSseFrame(frame: string, onEvent: StreamEventHandler) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (!dataLines.length) return
  const raw = dataLines.join('\n')
  try {
    onEvent(event, JSON.parse(raw))
  } catch {
    onEvent(event, raw)
  }
}

async function responseError(response: Response) {
  const raw = await response.text()
  try {
    const body = JSON.parse(raw) as Partial<ApiResponse<unknown>>
    return body.message || `请求失败（HTTP ${response.status}）`
  } catch {
    return raw || `请求失败（HTTP ${response.status}）`
  }
}
