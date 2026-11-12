type CsrfToken = { headerName: string; token: string }

// Share only an in-flight request. Each later POST obtains a fresh token, including after
// session expiry or the token rotation that login/logout will introduce.
let pending: Promise<CsrfToken> | undefined

async function loadToken(signal?: AbortSignal | null): Promise<CsrfToken> {
  const response = await fetch('/api/auth/csrf', {
    credentials: 'same-origin',
    cache: 'no-store',
    referrerPolicy: 'no-referrer',
    signal,
  })
  if (!response.ok) throw new Error('Unable to initialize request protection.')
  const data = await response.json()
  if (data?.headerName !== 'X-CSRF-TOKEN' || typeof data?.token !== 'string' || !data.token) {
    throw new Error('Invalid request protection response.')
  }
  return data as CsrfToken
}

export async function postWithCsrf(path: string, options: Omit<RequestInit, 'method'>): Promise<Response> {
  pending ??= loadToken(options.signal).finally(() => { pending = undefined })
  const csrf = await pending
  const headers = new Headers(options.headers)
  headers.set(csrf.headerName, csrf.token)
  return fetch(path, {
    ...options,
    method: 'POST',
    headers,
    credentials: 'same-origin',
    cache: 'no-store',
    referrerPolicy: 'no-referrer',
  })
}
