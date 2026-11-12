export type ResendVerificationResponse = { message: string; retryAfterSeconds: number }

export class ResendVerificationError extends Error {
  readonly emailError?: string

  constructor(message: string, emailError?: string) {
    super(message)
    this.name = 'ResendVerificationError'
    this.emailError = emailError
  }
}

export async function resendVerification(email: string): Promise<ResendVerificationResponse> {
  let response: Response
  try {
    response = await fetch('/api/auth/resend-verification', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
      signal: AbortSignal.timeout(15000),
      referrerPolicy: 'no-referrer',
      cache: 'no-store',
    })
  } catch {
    throw new ResendVerificationError('Unable to reach the server. Please check your connection and try again.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const emailError = response.status === 400 && typeof data?.fieldErrors?.email === 'string'
      ? data.fieldErrors.email : undefined
    throw new ResendVerificationError(emailError ? 'Please check your email address.'
      : 'Unable to request a confirmation email. Please try again later.', emailError)
  }
  if (response.status !== 202 || typeof data?.message !== 'string'
    || !Number.isInteger(data?.retryAfterSeconds) || data.retryAfterSeconds < 1 || data.retryAfterSeconds > 3600) {
    throw new ResendVerificationError('The server returned an unexpected response. Please try again later.')
  }
  return data as ResendVerificationResponse
}
