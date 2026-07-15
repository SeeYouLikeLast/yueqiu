const DEFAULT_AMAP_JS_KEY = '0b3dedfd34e183c63be3e7206dc5560c'
const AMAP_JS_KEY = import.meta.env.VITE_AMAP_JS_KEY || DEFAULT_AMAP_JS_KEY
const AMAP_SCRIPT_ID = 'amap-js-api'

export type PreciseLocation = {
  lng: number
  lat: number
  accuracy: number
  source: 'amap' | 'browser'
}

type AMapPosition =
  | string
  | [number, number]
  | {
      lng?: number
      lat?: number
      getLng?: () => number
      getLat?: () => number
    }

type AMapGeolocationResult = {
  position?: AMapPosition
  accuracy?: number
  message?: string
  info?: string
}

type AMapGeolocation = {
  getCurrentPosition: (callback: (status: string, result: AMapGeolocationResult) => void) => void
}

type AMapNamespace = {
  plugin: (name: string | string[], callback: () => void) => void
  Geolocation: new (options: Record<string, unknown>) => AMapGeolocation
}

type AMapWindow = Window & {
  AMap?: AMapNamespace
}

let amapLoadPromise: Promise<AMapNamespace> | null = null

export async function locateWithAmapFirst(): Promise<PreciseLocation> {
  // 浏览器只允许安全上下文读取精确位置；公网 HTTP 会被当作权限拒绝。
  if (!window.isSecureContext) {
    throw new Error('当前使用 HTTP 访问，浏览器仅允许 HTTPS 网站获取精确位置')
  }

  try {
    return await locateByAmap()
  } catch (amapError) {
    try {
      return await locateByBrowser()
    } catch (browserError) {
      throw new Error(locationErrorMessage(browserError, amapError))
    }
  }
}

function locateByAmap(): Promise<PreciseLocation> {
  return loadAmap().then((AMap) => new Promise((resolve, reject) => {
    AMap.plugin('AMap.Geolocation', () => {
      const geolocation = new AMap.Geolocation({
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0,
        convert: true,
        GeoLocationFirst: true,
        noIpLocate: 2,
        showCircle: true,
        zoomToAccuracy: true,
        position: 'RB'
      })

      geolocation.getCurrentPosition((status, result) => {
        if (status !== 'complete') {
          reject(new Error(result.message || result.info || '高德定位失败'))
          return
        }

        try {
          const position = normalizeAmapPosition(result.position)
          resolve({
            ...position,
            accuracy: Math.round(Number(result.accuracy || 0)),
            source: 'amap'
          })
        } catch (error) {
          reject(error)
        }
      })
    })
  }))
}

function loadAmap(): Promise<AMapNamespace> {
  const current = (window as AMapWindow).AMap
  if (current?.Geolocation) return Promise.resolve(current)
  if (amapLoadPromise) return amapLoadPromise

  amapLoadPromise = new Promise((resolve, reject) => {
    const existing = document.getElementById(AMAP_SCRIPT_ID) as HTMLScriptElement | null
    const finish = () => {
      const AMap = (window as AMapWindow).AMap
      if (AMap) {
        resolve(AMap)
      } else {
        reject(new Error('高德 JS API 加载失败'))
      }
    }

    if (existing) {
      existing.addEventListener('load', finish, { once: true })
      existing.addEventListener('error', () => reject(new Error('高德 JS API 加载失败')), { once: true })
      return
    }

    const script = document.createElement('script')
    script.id = AMAP_SCRIPT_ID
    script.async = true
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(AMAP_JS_KEY)}&plugin=AMap.Geolocation`
    script.onload = finish
    script.onerror = () => reject(new Error('高德 JS API 加载失败'))
    document.head.appendChild(script)
  })

  return amapLoadPromise
}

function normalizeAmapPosition(position?: AMapPosition) {
  if (!position) throw new Error('高德定位结果缺少坐标')

  if (typeof position === 'string') {
    const [lng, lat] = position.split(',').map(Number)
    return normalizeLngLat(lng, lat)
  }

  if (Array.isArray(position)) {
    return normalizeLngLat(Number(position[0]), Number(position[1]))
  }

  const lng = typeof position.getLng === 'function' ? position.getLng() : Number(position.lng)
  const lat = typeof position.getLat === 'function' ? position.getLat() : Number(position.lat)
  return normalizeLngLat(lng, lat)
}

function normalizeLngLat(lng: number, lat: number) {
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    throw new Error('定位坐标格式不正确')
  }
  return {
    lng: Number(lng.toFixed(6)),
    lat: Number(lat.toFixed(6))
  }
}

function locateByBrowser(): Promise<PreciseLocation> {
  if (!navigator.geolocation) {
    return Promise.reject(new Error('当前浏览器不支持定位'))
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({
        lng: Number(position.coords.longitude.toFixed(6)),
        lat: Number(position.coords.latitude.toFixed(6)),
        accuracy: Math.round(position.coords.accuracy || 0),
        source: 'browser'
      }),
      reject,
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0
      }
    )
  })
}

function locationErrorMessage(browserError: unknown, amapError: unknown) {
  if (typeof browserError === 'object' && browserError !== null && 'code' in browserError) {
    const code = Number((browserError as { code: number }).code)
    if (code === 1) return '定位权限被拒绝，请在浏览器地址栏允许位置权限'
    if (code === 2) return '暂时无法获取当前位置，请检查系统定位服务'
    if (code === 3) return '定位超时，请稍后重试'
  }

  const amapMessage = amapError instanceof Error ? amapError.message : ''
  const browserMessage = browserError instanceof Error ? browserError.message : ''
  return amapMessage || browserMessage || '获取当前位置失败'
}
