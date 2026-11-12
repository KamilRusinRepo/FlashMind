import { isAuthUser, type AuthUser } from './authUser'
import { postWithCsrf } from './csrf'

export async function getCurrentUser(): Promise<AuthUser | null> {
  const response = await fetch('/api/auth/me', {
    credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer', signal: AbortSignal.timeout(15000),
  })
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Unable to check your session. Please try again.')
  const data = await response.json().catch(() => null)
  if (!isAuthUser(data)) throw new Error('The server returned an unexpected session response.')
  return data
}

export async function logoutSession(): Promise<void> {
  const response = await postWithCsrf('/api/auth/logout', { signal: AbortSignal.timeout(15000) })
  if (response.status !== 204) throw new Error('Unable to sign out. Please try again.')
}
