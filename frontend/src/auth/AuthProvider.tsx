import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { AuthUser } from '../api/authUser'
import { loginAccount } from '../api/login'
import { getCurrentUser, logoutSession } from '../api/session'
import { AuthContext, type AuthState } from './AuthContext'

function resolved(user: AuthUser | null): AuthState {
  return { status: user ? 'authenticated' : 'anonymous', user, error: '' }
}

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null, error: '' })
  const [isLoggingOut, setLoggingOut] = useState(false)
  const revision = useRef(0)
  const initialRequest = useRef<Promise<AuthUser | null> | null>(null)
  const signingOut = useRef(false)

  useEffect(() => {
    let active = true
    const version = revision.current
    // StrictMode reuses this request. A later login/logout must win over its delayed result.
    initialRequest.current ??= getCurrentUser()
    initialRequest.current.then(user => {
      if (active && version === revision.current) setState(resolved(user))
    }).catch(() => {
      if (active && version === revision.current) setState({ status: 'error', user: null, error: 'Unable to check your session. Please try again.' })
    })
    return () => { active = false }
  }, [])

  const refresh = useCallback(() => {
    const version = ++revision.current
    setState({ status: 'loading', user: null, error: '' })
    getCurrentUser().then(user => {
      if (version === revision.current) setState(resolved(user))
    }).catch(() => {
      if (version === revision.current) setState({ status: 'error', user: null, error: 'Unable to check your session. Please try again.' })
    })
  }, [])

  const signIn = useCallback(async (login: string, password: string) => {
    const user = await loginAccount(login, password)
    ++revision.current
    setState(resolved(user))
  }, [])

  const signOut = useCallback(async () => {
    if (signingOut.current) return
    signingOut.current = true
    ++revision.current
    setLoggingOut(true)
    try {
      await logoutSession()
      setState(resolved(null))
    } catch {
      // A network failure is not confirmation that the server session was invalidated.
      setState(current => ({ ...current, error: 'Unable to sign out. Please check your connection and try again.' }))
    } finally {
      setLoggingOut(false)
      signingOut.current = false
    }
  }, [])

  return <AuthContext value={{ ...state, isLoggingOut, signIn, signOut, refresh }}>{children}</AuthContext>
}
