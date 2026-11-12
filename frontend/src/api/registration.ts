import { postWithCsrf } from './csrf'

export type RegisterRequest = {
  username: string
  email: string
  password: string
}

export type RegisterResponse = {
  id: number
  username: string
  email: string
  emailVerified: boolean
  createdAt: string
}

export class RegistrationError extends Error {
  readonly fieldErrors: Record<string, string>

  constructor(message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'RegistrationError'
    this.fieldErrors = fieldErrors
  }
}

export async function registerAccount(request: RegisterRequest): Promise<RegisterResponse> {
  let response: Response
  try {
    response = await postWithCsrf('/api/auth/register', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new RegistrationError('Unable to reach the server. Please check your connection and try again.')
  }

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const fieldErrors: Record<string, string> = {}
    for (const field of ['username', 'email', 'password']) {
      if (typeof data?.fieldErrors?.[field] === 'string') {
        fieldErrors[field] = data.fieldErrors[field]
      }
    }
    const message = response.status >= 500
      ? 'Registration is temporarily unavailable. Please try again later.'
      : typeof data?.message === 'string' ? data.message : 'Unable to create your account. Please try again.'
    throw new RegistrationError(message, fieldErrors)
  }

  if (typeof data?.id !== 'number' || typeof data?.username !== 'string' || typeof data?.email !== 'string') {
    throw new RegistrationError('The server returned an unexpected response. Please try again later.')
  }
  return data as RegisterResponse
}
