import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '@/lib/types'

/**
 * Auth state lives in localStorage via Zustand's persist middleware so a
 * page refresh keeps the user signed in.
 *
 * Important: the token is the source of truth — `user` is a UI convenience
 * (display name, role badge) that we refresh from /api/v1/auth/me when
 * we boot a session. If the token expires the next API call returns 401
 * and the axios interceptor clears the store.
 */
interface AuthState {
  token: string | null
  user: User | null
  tenantSlug: string | null
  signIn: (payload: { token: string; user: User; tenantSlug: string }) => void
  signOut: () => void
  setUser: (user: User) => void
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      tenantSlug: null,
      signIn: ({ token, user, tenantSlug }) =>
        set({ token, user, tenantSlug }),
      signOut: () => set({ token: null, user: null, tenantSlug: null }),
      setUser: (user) => set({ user }),
      isAuthenticated: () => Boolean(get().token),
    }),
    {
      name: 'eunomia.auth',
      // Only persist these fields — derived helpers don't need storage.
      partialize: (state) => ({
        token: state.token,
        user: state.user,
        tenantSlug: state.tenantSlug,
      }),
    },
  ),
)
