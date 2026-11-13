import { postWithCsrf } from './csrf'

export class ResetPasswordError extends Error {
  readonly passwordError?: string
  readonly invalidToken: boolean

  constructor(message: string, invalidToken = false, passwordError?: string) {
    super(message)
    this.name = 'ResetPasswordError'
    this.invalidToken = invalidToken
    this.passwordError = passwordError
  }
}

export async function resetPassword(token: string, password: string): Promise<void> {
  let response: Response
  try {
    response = await postWithCsrf('/api/auth/reset-password', {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, password }),
      signal: AbortSignal.timeout(15000), referrerPolicy: 'no-referrer', cache: 'no-store',
    })
  } catch {
    throw new ResetPasswordError('Unable to reach the server. Please try again. If your previous request succeeded, request a new reset link.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 400 && typeof data?.fieldErrors?.token === 'string') {
      throw new ResetPasswordError('This reset link is invalid, expired or has already been used. Please request a new link.', true)
    }
    const passwordError = response.status === 400 && typeof data?.fieldErrors?.password === 'string' ? data.fieldErrors.password : undefined
    throw new ResetPasswordError(passwordError ? 'Please check your new password.' : 'Unable to reset your password. Please try again.', false, passwordError)
  }
  if (response.status !== 200 || data?.passwordReset !== true) {
    throw new ResetPasswordError('The server returned an unexpected response. Request a new reset link if your previous request succeeded.')
  }
}
