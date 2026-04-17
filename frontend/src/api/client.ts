import type { ApiEnvelope, AuthResponse } from './types'

const ACCESS_KEY = 'tp_access_token'
const REFRESH_KEY = 'tp_refresh_token'

export class ApiError extends Error {
  readonly httpStatus: number
  readonly bodyCode?: number

  constructor(httpStatus: number, message: string, bodyCode?: number) {
    super(message)
    this.name = 'ApiError'
    this.httpStatus = httpStatus
    this.bodyCode = bodyCode
  }
}

export function getBaseUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '')
  if (!base) {
    throw new Error('VITE_API_BASE_URL is not set')
  }
  return base
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(ACCESS_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

async function parseEnvelope<T>(res: Response): Promise<ApiEnvelope<T | null>> {
  const text = await res.text()
  if (!text) {
    throw new ApiError(res.status, res.statusText || 'Empty response')
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(text) as ApiEnvelope<T | null>
  } catch {
    throw new ApiError(res.status, text.slice(0, 200))
  }
  const env = parsed as ApiEnvelope<T | null>
  if (!res.ok) {
    throw new ApiError(res.status, env.message || res.statusText, env.code)
  }
  return env
}

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = `${getBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const exec = () => fetch(url, { ...init, headers })

  let res = await exec()

  if (res.status === 401 && !path.includes('/api/auth/login') && !path.includes('/api/auth/refresh')) {
    const refreshed = await tryRefresh()
    if (refreshed) {
      const retryHeaders = new Headers(init.headers)
      if (!retryHeaders.has('Content-Type') && init.body && !(init.body instanceof FormData)) {
        retryHeaders.set('Content-Type', 'application/json')
      }
      const t = getAccessToken()
      if (t) retryHeaders.set('Authorization', `Bearer ${t}`)
      res = await fetch(url, { ...init, headers: retryHeaders })
    }
  }

  const env = await parseEnvelope<T>(res)
  return env.data as T
}

async function tryRefresh(): Promise<boolean> {
  const refresh = getRefreshToken()
  if (!refresh) return false
  const url = `${getBaseUrl()}/api/auth/refresh`
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
    })
    const env = await parseEnvelope<AuthResponse>(res)
    const data = env.data
    if (!data?.accessToken || !data.refreshToken) return false
    setTokens(data.accessToken, data.refreshToken)
    return true
  } catch {
    return false
  }
}

/** Raw fetch with Authorization; use for binary endpoints (no JSON envelope). */
export async function requestRaw(path: string, init: RequestInit = {}): Promise<Response> {
  const url = `${getBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let res = await fetch(url, { ...init, headers })
  if (res.status === 401) {
    const ok = await tryRefresh()
    if (ok) {
      const h2 = new Headers(init.headers)
      const t = getAccessToken()
      if (t) h2.set('Authorization', `Bearer ${t}`)
      res = await fetch(url, { ...init, headers: h2 })
    }
  }
  return res
}
