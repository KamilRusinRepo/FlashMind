import { postWithCsrf } from './csrf'
import { EmailVerificationError } from './emailVerification'

export type PendingEmailChange = { pendingEmail: string | null; expiresAt: string | null; retryAfterSeconds: number }
export class EmailChangeError extends Error {
  readonly status: number
  readonly fieldErrors: Partial<Record<'email' | 'currentPassword', string>>
  constructor(message: string, status = 0, fieldErrors: EmailChangeError['fieldErrors'] = {}) {
    super(message); this.name = 'EmailChangeError'; this.status = status; this.fieldErrors = fieldErrors
  }
}

function readPending(data: unknown): PendingEmailChange {
  if (!data || typeof data !== 'object') throw new EmailChangeError('Unable to confirm the email change status. Reopen this window to check it.')
  const value = data as PendingEmailChange
  if (!Number.isInteger(value.retryAfterSeconds) || value.retryAfterSeconds < 0 || value.retryAfterSeconds > 60
    || !(value.pendingEmail === null && value.expiresAt === null || typeof value.pendingEmail === 'string' && typeof value.expiresAt === 'string' && Number.isFinite(Date.parse(value.expiresAt)))) {
    throw new EmailChangeError('Unable to confirm the email change status. Reopen this window to check it.')
  }
  return value
}

async function errorFor(response: Response): Promise<EmailChangeError> {
  const data = await response.json().catch(() => null)
  const fields: EmailChangeError['fieldErrors'] = {}
  for (const field of ['email', 'currentPassword'] as const) if (typeof data?.fieldErrors?.[field] === 'string') fields[field] = data.fieldErrors[field]
  return new EmailChangeError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 429 ? 'Wait 60 seconds before requesting another confirmation.'
    : Object.keys(fields).length ? 'Please check the highlighted fields.' : 'Unable to request an email change. Please try again.', response.status, fields)
}

export async function getPendingEmailChange(signal: AbortSignal): Promise<PendingEmailChange> {
  let response: Response
  try {
    response = await fetch('/api/users/me/email-change', { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new EmailChangeError('Unable to check pending changes. Check your connection and try again.') }
  if (!response.ok) throw await errorFor(response)
  return readPending(await response.json().catch(() => null))
}

export async function requestEmailChange(email: string, currentPassword: string): Promise<PendingEmailChange> {
  let response: Response
  try {
    response = await postWithCsrf('/api/users/me/email-change', {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, currentPassword }), signal: AbortSignal.timeout(15000),
    })
  } catch { throw new EmailChangeError('The request could not be confirmed. Reopen this window to check the saved status before trying again.') }
  if (!response.ok) throw await errorFor(response)
  const data = readPending(await response.json().catch(() => null))
  if (response.status !== 202 || data.pendingEmail === null) throw new EmailChangeError('The request could not be confirmed. Reopen this window to check its status.')
  return data
}

export async function confirmEmailChange(token: string): Promise<void> {
  let response: Response
  try {
    response = await postWithCsrf('/api/auth/confirm-email-change', { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }), signal: AbortSignal.timeout(15000) })
  } catch { throw new EmailVerificationError('Confirmation could not be confirmed. Try signing in with your new address to check whether it was changed.', true) }
  if (!response.ok) throw new EmailVerificationError(response.status === 400 ? 'This link is invalid, expired or already used. Request another change in Settings.'
    : response.status === 409 ? 'This address is no longer available. Request a different address in Settings.'
    : 'Unable to confirm the email change. Please try again later.', response.status !== 400 && response.status !== 409)
  const data = await response.json().catch(() => null)
  if (data?.emailVerified !== true) throw new EmailVerificationError('Unable to confirm the result. Try signing in with your new address.', true)
}
