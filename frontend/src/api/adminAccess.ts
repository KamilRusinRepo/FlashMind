export class AdminAccessError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'AdminAccessError'; this.status = status }
}

export async function checkAdminAccess(signal: AbortSignal): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/admin/access', { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new AdminAccessError('Unable to check administrator access. Please check your connection and try again.') }
  if (response.status === 204) return
  throw new AdminAccessError('Unable to check administrator access. Please try again.', response.status)
}
