import { postWithCsrf } from './csrf'
import { isAuthUser, type AuthUser } from './authUser'

export type LoginResponse = AuthUser

export class LoginError extends Error {
  readonly fieldErrors: Record<string, string>
  readonly verificationEmail?: string

  constructor(message: string, fieldErrors: Record<string, string> = {}, verificationEmail?: string) {
    super(message)
    this.name = 'LoginError'
    this.fieldErrors = fieldErrors
    this.verificationEmail = verificationEmail
  }
}

export async function loginAccount(login: string, password: string): Promise<LoginResponse> {
  let response: Response
  try {
    response = await postWithCsrf('/api/auth/login', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login, password }),
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new LoginError('Unable to reach the server. Please check your connection and try again.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const fields: Record<string, string> = {}
    for (const field of ['login', 'password']) {
      if (response.status === 400 && typeof data?.fieldErrors?.[field] === 'string') fields[field] = data.fieldErrors[field]
    }
    const verificationEmail = response.status === 403 && data?.code === 'EMAIL_VERIFICATION_REQUIRED'
      && typeof data?.email === 'string' && data.email.length > 0 ? data.email : undefined
    throw new LoginError(response.status >= 500 ? 'Sign in is temporarily unavailable. Please try again later.'
      : typeof data?.message === 'string' ? data.message : 'Unable to sign in. Please try again.', fields, verificationEmail)
  }
  if (!isAuthUser(data)) {
    throw new LoginError('The server returned an unexpected response. Please try again later.')
  }
  return data as LoginResponse
}
