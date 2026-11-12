import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { EmailVerificationError, verifyEmail } from '../api/emailVerification'

type VerificationState =
  | { status: 'pending' }
  | { status: 'success' }
  | { status: 'error'; message: string; retryable: boolean }

export default function EmailVerificationPage() {
  const [params] = useSearchParams()
  const tokens = params.getAll('token')
  const token = tokens.length === 1 ? tokens[0] : ''
  const [attempt, setAttempt] = useState(0)

  // A new link or an explicit retry gets its own state and request.
  return <VerificationResult key={`${token}:${attempt}`} token={token} onRetry={() => setAttempt(value => value + 1)} />
}

function VerificationResult({ token, onRetry }: { token: string; onRetry: () => void }) {
  const validFormat = /^[A-Za-z0-9_-]{43}$/.test(token)
  const [state, setState] = useState<VerificationState>(() => validFormat
    ? { status: 'pending' }
    : { status: 'error', message: 'The confirmation link is missing or invalid. Open the full link from your email.', retryable: false })
  const request = useRef<Promise<void> | null>(null)

  useEffect(() => {
    if (!validFormat) return
    let active = true
    // Reuse the request across StrictMode's effect setup/cleanup cycle.
    // Aborting and starting again could consume a single-use token twice.
    request.current ??= verifyEmail(token)
    request.current.then(() => {
      if (active) setState({ status: 'success' })
    }).catch((error: unknown) => {
      if (!active) return
      setState({
        status: 'error',
        message: error instanceof EmailVerificationError ? error.message : 'Unable to confirm your email. Please try again.',
        retryable: error instanceof EmailVerificationError ? error.retryable : true,
      })
    })
    return () => { active = false }
  }, [token, validFormat])

  return (
    <div className="auth-page">
      <Link to="/" className="back-link"><span aria-hidden="true">←</span> Back to home</Link>
      <section className="auth-card" aria-labelledby="verification-title" aria-busy={state.status === 'pending'}>
        <div className="auth-heading">
          <span className="eyebrow">EMAIL CONFIRMATION</span>
          <h1 id="verification-title">{state.status === 'success' ? 'Email confirmed' : state.status === 'pending' ? 'Confirming your email' : 'Unable to confirm email'}</h1>
          <p>{state.status === 'pending' ? 'Please wait while we check your confirmation link.' : state.status === 'success' ? 'Your email address has been confirmed.' : 'Check your confirmation link below.'}</p>
        </div>
        <div className="registration-success">
          {state.status === 'pending' && <p className="form-notice" role="status">Confirming your email…</p>}
          {state.status === 'success' && (
            <div className="form-notice form-success" role="status">
              <p>Your email confirmation is complete. You can close this page.</p>
              <p>Sign in will be available soon.</p>
            </div>
          )}
          {state.status === 'error' && (
            <>
              <p className="form-notice form-error" role="alert">{state.message}</p>
              {state.retryable && <button type="button" className="button button-primary button-full" onClick={onRetry}>Try again</button>}
            </>
          )}
          <Link className="button button-secondary button-full" to="/">Back to home</Link>
        </div>
      </section>
    </div>
  )
}
