import { postWithCsrf } from './csrf'

type ForgotPasswordResponse = { message: string; retryAfterSeconds: number }

export class ForgotPasswordError extends Error {
  readonly emailError?: string

  constructor(message: string, emailError?: string) {
    super(message)
    this.name = 'ForgotPasswordError'
    this.emailError = emailError
  }
}

export async function requestPasswordReset(email: string): Promise<ForgotPasswordResponse> {
  let response: Response
  try {
    response = await postWithCsrf('/api/auth/forgot-password', {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }),
      signal: AbortSignal.timeout(15000), referrerPolicy: 'no-referrer', cache: 'no-store',
    })
  } catch {
    throw new ForgotPasswordError('Unable to reach the server. Please check your connection and try again.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const emailError = response.status === 400 && typeof data?.fieldErrors?.email === 'string' ? data.fieldErrors.email : undefined
    throw new ForgotPasswordError(emailError ? 'Please check your email address.'
      : 'Unable to request a password reset. Please try again later.', emailError)
  }
  if (response.status !== 202 || typeof data?.message !== 'string' || !Number.isInteger(data?.retryAfterSeconds)
      || data.retryAfterSeconds < 1 || data.retryAfterSeconds > 3600) {
    throw new ForgotPasswordError('The server returned an unexpected response. Please try again later.')
  }
  return data as ForgotPasswordResponse
}
