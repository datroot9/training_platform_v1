import { requestJson, setTokens } from '../client'
import type { AuthResponse } from '../types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function logout(refreshToken: string): Promise<void> {
  await requestJson<null>('/api/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  })
}

export function applyAuthResponse(data: AuthResponse): void {
  setTokens(data.accessToken, data.refreshToken)
}
