import { api } from './api'
import type { AuthResponse, User } from './types'

export interface LoginPayload {
  email: string
  password: string
  tenantSlug: string
}

export interface RegisterPayload extends LoginPayload {
  displayName: string
}

export async function login(payload: LoginPayload): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/v1/auth/login', payload)
  return res.data
}

export async function register(payload: RegisterPayload): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/v1/auth/register', payload)
  return res.data
}

export async function fetchMe(): Promise<User> {
  const res = await api.get<User>('/v1/auth/me')
  return res.data
}
