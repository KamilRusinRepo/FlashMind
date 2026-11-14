import { postWithCsrf } from './csrf'

export type PasswordChangeRequest = { currentPassword: string; newPassword: string; confirmPassword: string }
export type PasswordChangeFields = Partial<Record<keyof PasswordChangeRequest, string>>
export class PasswordChangeError extends Error {
  readonly status: number
  readonly fieldErrors: PasswordChangeFields
  constructor(message: string, status = 0, fieldErrors: PasswordChangeFields = {}) {
    super(message); this.name = 'PasswordChangeError'; this.status = status; this.fieldErrors = fieldErrors
  }
}

export async function changePassword(input: PasswordChangeRequest): Promise<void> {
  let response: Response
  try {
    response = await postWithCsrf('/api/users/me/password', {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input), signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new PasswordChangeError('The change could not be confirmed. Try signing in with your new password before trying again.')
  }
  if (response.status === 204) return
  const data = await response.json().catch(() => null)
  const fields: PasswordChangeFields = {}
  for (const field of ['currentPassword', 'newPassword', 'confirmPassword'] as const) {
    if (typeof data?.fieldErrors?.[field] === 'string') fields[field] = data.fieldErrors[field]
  }
  throw new PasswordChangeError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : Object.keys(fields).length ? 'Please check the highlighted fields.'
    : 'Unable to change your password. Please try again.', response.status, fields)
}
