import { postWithCsrf } from './csrf'

export type LoginResponse = { id: number; username: string; email: string; roles: string[] }

export class LoginError extends Error {
  readonly fieldErrors: Record<string, string>

  constructor(message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'LoginError'
    this.fieldErrors = fieldErrors
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
    throw new LoginError(response.status >= 500 ? 'Sign in is temporarily unavailable. Please try again later.'
      : typeof data?.message === 'string' ? data.message : 'Unable to sign in. Please try again.', fields)
  }
  if (typeof data?.id !== 'number' || typeof data?.username !== 'string' || typeof data?.email !== 'string'
    || !Array.isArray(data?.roles) || !data.roles.every((role: unknown) => typeof role === 'string')) {
    throw new LoginError('The server returned an unexpected response. Please try again later.')
  }
  return data as LoginResponse
}
