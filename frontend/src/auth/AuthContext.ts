import { createContext, useContext } from 'react'
import type { AuthUser } from '../api/authUser'

export type AuthState = {
  status: 'loading' | 'authenticated' | 'anonymous' | 'error'
  user: AuthUser | null
  error: string
}

export type AuthContextValue = AuthState & {
  isLoggingOut: boolean
  signIn: (login: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  changeUsername: (username: string) => Promise<AuthUser>
  refresh: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth requires AuthProvider.')
  return context
}
