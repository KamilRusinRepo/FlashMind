export class EmailVerificationError extends Error {
  readonly retryable: boolean

  constructor(message: string, retryable: boolean) {
    super(message)
    this.name = 'EmailVerificationError'
    this.retryable = retryable
  }
}

export async function verifyEmail(token: string): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/auth/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
      signal: AbortSignal.timeout(15000),
      referrerPolicy: 'no-referrer',
      cache: 'no-store',
    })
  } catch {
    throw new EmailVerificationError('Unable to reach the server. Please check your connection and try again.', true)
  }

  if (!response.ok) {
    if (response.status === 400) {
      throw new EmailVerificationError('This confirmation link is invalid, has expired or has already been used.', false)
    }
    throw new EmailVerificationError('Email confirmation is temporarily unavailable. Please try again later.', true)
  }

  const data = await response.json().catch(() => null)
  if (data?.emailVerified !== true) {
    throw new EmailVerificationError('The server returned an unexpected response. Please try again later.', true)
  }
}
