import { useLanguage } from '../i18n/LanguageContext'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { resendVerification, ResendVerificationError } from '../api/resendVerification'
import FormField from './FormField'

type Props = { email?: string; initialCooldownSeconds?: number }

export default function ResendVerificationForm({ email, initialCooldownSeconds = 0 }: Props) {
  const { t } = useLanguage()
  const [remaining, setRemaining] = useState(initialCooldownSeconds)
  const [initialDeadline] = useState(() => Date.now() + initialCooldownSeconds * 1000)
  const deadline = useRef(initialDeadline)
  const submitting = useRef(false)
  const [sending, setSending] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [emailError, setEmailError] = useState('')

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
    const address = (email ?? String(new FormData(form).get('email') ?? '')).trim()
    setEmailError('')
    setError('')
    setNotice('')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address) || address.length > 255) {
      setEmailError('Enter a valid email address.')
      form.querySelector<HTMLInputElement>('[name="email"]')?.focus()
      return
    }
    submitting.current = true
    setSending(true)
    try {
      const result = await resendVerification(address)
      deadline.current = Date.now() + result.retryAfterSeconds * 1000
      setRemaining(result.retryAfterSeconds)
      setNotice(result.message)
    } catch (cause) {
      setError(cause instanceof ResendVerificationError ? cause.message : 'Unable to request a confirmation email. Please try again.')
      if (cause instanceof ResendVerificationError && cause.emailError) {
        setEmailError(cause.emailError)
        requestAnimationFrame(() => form.querySelector<HTMLInputElement>('[name="email"]')?.focus())
      }
    } finally {
      submitting.current = false
      setSending(false)
    }
  }

  return (
    <div className="resend-verification">
      <h2>{t("Need a new confirmation link?")}</h2>
      <p className="field-hint">{t("Check your spam folder first. Requesting a new link replaces earlier confirmation links.")}</p>
      <form className="auth-form" noValidate aria-busy={sending} onSubmit={handleSubmit}>
        {!email && <FormField id="resend-email" name="email" label={t("Email")} type="email" placeholder={t("name@example.com")}
          autoComplete="email" maxLength={255} required disabled={sending} error={t(emailError)} />}
        {email && emailError && <p className="field-error" role="alert">{t(emailError)}</p>}
        {notice && <p className="form-notice form-success" role="status">{t(notice)}</p>}
        {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
        <button type="submit" className="button button-secondary button-full" disabled={sending || remaining > 0}>
          {sending ? t("Requesting email…") : remaining > 0 ? t("Resend in {0}s", [remaining]) : t("Resend verification email")}
        </button>
      </form>
    </div>
  )
}
