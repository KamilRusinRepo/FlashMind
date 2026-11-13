import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import FormField from '../components/FormField'
import { requestPasswordReset, ForgotPasswordError } from '../api/forgotPassword'

export default function ForgotPasswordPage() {
  const [emailError, setEmailError] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const [remaining, setRemaining] = useState(0)
  const deadline = useRef(0)
  const submitting = useRef(false)

  useEffect(() => {
    const timer = window.setInterval(() => {
      setRemaining(Math.max(0, Math.ceil((deadline.current - Date.now()) / 1000)))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || Date.now() < deadline.current) return
    const form = event.currentTarget
    const email = String(new FormData(form).get('email') ?? '').trim()
    const validationError = !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
      ? 'Enter a valid email address.' : email.length > 255 ? 'Use at most 255 characters.' : ''
    setEmailError(validationError)
    setError('')
    setNotice('')
    if (validationError) {
      form.querySelector<HTMLInputElement>('[name="email"]')?.focus()
      return
    }
    submitting.current = true
    setSending(true)
    try {
      const result = await requestPasswordReset(email)
      deadline.current = Date.now() + result.retryAfterSeconds * 1000
      setRemaining(result.retryAfterSeconds)
      setNotice(result.message)
    } catch (cause) {
      setError(cause instanceof ForgotPasswordError ? cause.message : 'Unable to request a password reset. Please try again.')
      if (cause instanceof ForgotPasswordError && cause.emailError) {
        setEmailError(cause.emailError)
        requestAnimationFrame(() => form.querySelector<HTMLInputElement>('[name="email"]')?.focus())
      }
    } finally {
      submitting.current = false
      setSending(false)
    }
  }

  return <div className="auth-page">
    <Link to="/login" className="back-link"><span aria-hidden="true">←</span> Back to sign in</Link>
    <section className="auth-card" aria-labelledby="forgot-password-title">
      <div className="auth-heading">
        <span className="eyebrow">LET’S GET YOU BACK</span>
        <h1 id="forgot-password-title">Forgot password?</h1>
        <p>Enter your email address to request a password reset link.</p>
      </div>
      <form className="auth-form" noValidate aria-busy={sending} onSubmit={handleSubmit} onInput={() => { setEmailError(''); setNotice(''); setError('') }}>
        <FormField id="recovery-email" name="email" label="Email" type="email" placeholder="name@example.com"
          autoComplete="email" maxLength={255} required disabled={sending} error={emailError} />
        <p className="field-hint">Requesting a new reset link replaces earlier reset links. Please wait 60 seconds between requests.</p>
        {notice && <p className="form-notice" role="status">{notice}</p>}
        {error && <p className="form-notice form-error" role="alert">{error}</p>}
        <button className="button button-primary button-full" type="submit" disabled={sending || remaining > 0}>
          {sending ? 'Requesting reset link…' : remaining > 0 ? `Try again in ${remaining}s` : 'Send reset link'} <span aria-hidden="true">→</span>
        </button>
      </form>
    </section>
    <p className="auth-footer">Remember your password? <Link to="/login">Sign in</Link></p>
  </div>
}
