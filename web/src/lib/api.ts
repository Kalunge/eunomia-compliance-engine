import axios from 'axios'

// All API calls go through the Vite dev proxy in development (/api → :8080).
// In production, the static frontend is served from Spring Boot itself, so
// /api resolves on the same origin and the proxy is unnecessary.
export const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Auth token interceptor (no-op until Sprint 1 ships login). When a token is
// present in localStorage it gets attached to every outgoing request.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('eunomia.jwt')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
