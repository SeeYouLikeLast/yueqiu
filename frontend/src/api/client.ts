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
    // Nginx 上游不可用时可能返回 HTML。页面只展示可操作的中文提示，
    // 不能把完整网关错误页和服务器版本暴露给用户。
    throw new Error(formatResponseError(response, raw))
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
    return body.message || statusMessage(response.status)
  } catch {
    return formatResponseError(response, raw)
  }
}

function formatResponseError(response: Response, raw: string) {
  const contentType = response.headers.get('content-type')?.toLowerCase() || ''
  const looksLikeHtml = contentType.includes('text/html') || /^\s*<!doctype html/i.test(raw) || /^\s*<html/i.test(raw)
  if (looksLikeHtml || !raw.trim()) {
    return statusMessage(response.status)
  }
  return raw.trim()
}

function statusMessage(status: number) {
  if (status === 502 || status === 503) {
    return '服务正在启动或暂时不可用，请稍后重试'
  }
  if (status === 504) {
    return 'AI 助手响应超时，请稍后重试'
  }
  if (status === 429) {
    return '请求过于频繁，请稍后再试'
  }
  return `请求失败（HTTP ${status}）`
}
