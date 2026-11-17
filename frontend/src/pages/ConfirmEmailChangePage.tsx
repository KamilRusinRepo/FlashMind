import { useLanguage } from '../i18n/LanguageContext'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { confirmEmailChange } from '../api/emailChange'
import { EmailVerificationError } from '../api/emailVerification'
import { useAuth } from '../auth/AuthContext'

export default function ConfirmEmailChangePage() {
  const [params] = useSearchParams()
  const values = params.getAll('token')
  const token = values.length === 1 ? values[0] : ''
  const [attempt, setAttempt] = useState(0)
  return <Confirmation key={`${token}:${attempt}`} token={token} onRetry={() => setAttempt(value => value + 1)} />
}

function Confirmation({ token, onRetry }: { token: string; onRetry: () => void }) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const valid = /^[A-Za-z0-9_-]{43}$/.test(token)
  const [state, setState] = useState<'pending' | 'success' | 'error'>(valid ? 'pending' : 'error')
  const [message, setMessage] = useState(valid ? '' : 'The confirmation link is missing or invalid.')
  const [retryable, setRetryable] = useState(false)
  const request = useRef<Promise<void> | null>(null)
  useEffect(() => {
    if (!valid) return
    let active = true
    request.current ??= confirmEmailChange(token)
    request.current.then(() => {
      if (!active) return
      setState('success'); refresh()
    }).catch(cause => {
      if (!active) return
      setState('error'); setMessage(cause instanceof Error ? cause.message : 'Unable to confirm the email change.')
      setRetryable(cause instanceof EmailVerificationError && cause.retryable)
    })
    return () => { active = false }
  }, [token, valid, refresh])

  return <div className="auth-page"><section className="auth-card" aria-labelledby="email-change-title" aria-busy={state === 'pending'}>
    <div className="auth-heading"><span className="eyebrow">{t("EMAIL ADDRESS CHANGE")}</span>
      <h1 id="email-change-title">{state === 'success' ? t("Email address changed") : state === 'pending' ? t("Confirming your new email") : t("Unable to change email")}</h1></div>
    <div className="registration-success">
      {state === 'pending' && <p className="form-notice" role="status">{t("Checking your confirmation link…")}</p>}
      {state === 'success' && <p className="form-notice form-success" role="status">{t("Your new address is active. Sign in again using your new email address or username.")}</p>}
      {state === 'error' && <><p className="form-notice form-error" role="alert">{t(message)}</p>
        {retryable && <button className="button button-outline button-full" type="button" onClick={onRetry}>{t("Try again")}</button>}
        <Link className="button button-outline button-full" to="/settings">{t("Back to Settings")}</Link></>}
      <Link className="button button-primary button-full" to="/login">{t("Go to sign in")}</Link>
    </div>
  </section></div>
}
