import { isAuthUser, type AuthUser } from './authUser'
import { requestWithCsrf } from './csrf'

export class ProfileApiError extends Error {
  readonly status: number
  readonly usernameError: string
  constructor(message: string, status = 0, usernameError = '') {
    super(message); this.name = 'ProfileApiError'; this.status = status; this.usernameError = usernameError
  }
}

export async function updateUsername(username: string, userId: number): Promise<AuthUser> {
  let response: Response
  try {
    response = await requestWithCsrf('/api/users/me/username', 'PUT', {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username }), signal: AbortSignal.timeout(15000),
    })
  } catch { throw new ProfileApiError('Saving could not be confirmed. Reload Settings to check your username before trying again.') }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const fieldError = typeof data?.fieldErrors?.username === 'string' ? data.fieldErrors.username : ''
    throw new ProfileApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
      : fieldError ? 'Please check your username.'
      : response.status === 409 ? 'This username is unavailable. Please choose another one.'
      : 'Unable to save your username. Please try again.', response.status, fieldError)
  }
  if (response.status !== 200 || !isAuthUser(data) || data.id !== userId) throw new ProfileApiError('Saving could not be confirmed. Reload Settings to check your username.')
  return data
}
