import axios, { AxiosError } from 'axios'
import { useAuthStore } from '@/stores/authStore'

/**
 * Single axios instance for all backend calls.
 *
 * - Base URL is `/api` in dev (proxied to :8080 by Vite) and in prod
 *   (Spring Boot serves the SPA from the same origin).
 * - Request interceptor attaches the JWT from the auth store.
 * - Response interceptor sign-outs the user on 401 so a stale token
 *   doesn't leave them stuck on a "session active" UI while the
 *   backend disagrees.
 */
export const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Token rejected — clear local state so the router redirects to login.
      // Skip clearing if the failed call WAS the login/register itself
      // (those legitimately return 401 with wrong credentials).
      const url = error.config?.url ?? ''
      if (!url.includes('/auth/login') && !url.includes('/auth/register')) {
        useAuthStore.getState().signOut()
      }
    }
    return Promise.reject(error)
  },
)

export function isApiError(error: unknown): error is AxiosError<{ code: string; message: string }> {
  return axios.isAxiosError(error)
}

export function apiErrorMessage(error: unknown, fallback = 'Something went wrong.'): string {
  if (isApiError(error)) {
    return error.response?.data?.message ?? error.message ?? fallback
  }
  return fallback
}
