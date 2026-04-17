import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '../api/modules/auth'
import { clearTokens, getAccessToken, getRefreshToken } from '../api/client'
import type { AuthResponse, Role, StoredUser } from '../api/types'

const USER_KEY = 'tp_user'

function readStoredUser(): StoredUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    const u = JSON.parse(raw) as StoredUser
    if (u.role !== 'MENTOR' && u.role !== 'TRAINEE') return null
    return u
  } catch {
    return null
  }
}

function writeStoredUser(user: StoredUser): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

function clearStoredUser(): void {
  localStorage.removeItem(USER_KEY)
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<StoredUser | null>(readStoredUser())
  const mustChangePassword = ref(false)

  const isAuthenticated = computed(() => !!user.value && !!getAccessToken())

  function hydrateFromAuthResponse(data: AuthResponse): void {
    const role = data.role as Role
    if (role !== 'MENTOR' && role !== 'TRAINEE') {
      throw new Error(`Unsupported role: ${data.role}`)
    }
    const stored: StoredUser = {
      userId: data.userId,
      email: data.email,
      role,
    }
    user.value = stored
    writeStoredUser(stored)
    mustChangePassword.value = data.mustChangePassword
    authApi.applyAuthResponse(data)
  }

  async function login(email: string, password: string): Promise<void> {
    const data = await authApi.login(email, password)
    hydrateFromAuthResponse(data)
  }

  async function logout(): Promise<void> {
    const refresh = getRefreshToken()
    try {
      if (refresh) {
        await authApi.logout(refresh)
      }
    } finally {
      user.value = null
      mustChangePassword.value = false
      clearStoredUser()
      clearTokens()
    }
  }

  function restoreSession(): void {
    const u = readStoredUser()
    if (u && getAccessToken()) {
      user.value = u
    } else {
      clearStoredUser()
      clearTokens()
      user.value = null
    }
  }

  return {
    user,
    mustChangePassword,
    isAuthenticated,
    login,
    logout,
    restoreSession,
    hydrateFromAuthResponse,
  }
})
