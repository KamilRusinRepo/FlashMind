import { useLanguage } from '../i18n/LanguageContext'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import FormField from '../components/FormField'
import { resetPassword, ResetPasswordError } from '../api/resetPassword'
import { useAuth } from '../auth/AuthContext'

type PasswordErrors = Partial<Record<'password' | 'confirmation', string>>

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const values = params.getAll('token')
  const token = values.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(values[0]) ? values[0] : null

  // A different link must not retain the previous link's success/error or password fields.
  return <ResetPasswordForm key={token ?? 'invalid-link'} token={token} />
}

function ResetPasswordForm({ token }: { token: string | null }) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [errors, setErrors] = useState<PasswordErrors>({})
  const [error, setError] = useState('')
  const [invalidToken, setInvalidToken] = useState(false)
  const [success, setSuccess] = useState(false)
  const [sending, setSending] = useState(false)
  const submitting = useRef(false)
  const invalidLink = !token || invalidToken

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || invalidLink || success || submitting.current) return
    const form = event.currentTarget
    const data = new FormData(form)
    const password = String(data.get('password') ?? '')
    const confirmation = String(data.get('confirmation') ?? '')
    const nextErrors: PasswordErrors = {}
    if (!password.trim() || password.length < 8) nextErrors.password = 'Use at least 8 characters.'
    else if (new TextEncoder().encode(password).length > 72) nextErrors.password = 'Password must not exceed 72 UTF-8 bytes.'
    if (!confirmation || password !== confirmation) nextErrors.confirmation = 'Passwords must match.'
    setErrors(nextErrors)
    setError('')
    const firstError = Object.keys(nextErrors)[0]
    if (firstError) {
      form.querySelector<HTMLInputElement>(`[name="${firstError}"]`)?.focus()
      return
    }
    submitting.current = true
    setSending(true)
    try {
      await resetPassword(token, password)
      form.reset()
      setSuccess(true)
      // The backend revoked sessions for the affected account; refresh our shared UI state too.
      refresh()
    } catch (cause) {
      setError(cause instanceof ResetPasswordError ? cause.message : 'Unable to reset your password. Please try again.')
      if (cause instanceof ResetPasswordError) {
        setInvalidToken(cause.invalidToken)
        if (cause.passwordError) {
          setErrors({ password: cause.passwordError })
          requestAnimationFrame(() => form.querySelector<HTMLInputElement>('[name="password"]')?.focus())
        }
      }
    } finally {
      submitting.current = false
      setSending(false)
    }
  }

  return <div className="auth-page">
    <Link to="/login" className="back-link"><span aria-hidden="true">←</span> {t("Back to sign in")}</Link>
    <section className="auth-card" aria-labelledby="reset-password-title">
      <div className="auth-heading">
        <span className="eyebrow">{t("A FRESH START")}</span>
        <h1 id="reset-password-title">{success ? t("Password reset") : invalidLink ? t("Invalid reset link") : t("Set a new password")}</h1>
        <p>{success ? t("Your password has been changed.") : invalidLink ? t("Request a new link to reset your password.") : t("Choose a new password and enter it again to confirm.")}</p>
      </div>
      {success ? <div className="registration-success">
        <p className="form-notice form-success" role="status">{t("Your password has been reset. Previous sessions have been revoked. Sign in with your new password.")}</p>
        <Link className="button button-primary button-full" to="/login">{t("Go to sign in")}</Link>
      </div> : invalidLink ? <div className="registration-success">
        <p className="form-notice form-error" role="alert">{t(error) || t("This reset link is missing or invalid. Please use the complete link from your latest email.")}</p>
        <Link className="button button-primary button-full" to="/forgot-password">{t("Request a new reset link")}</Link>
      </div> : <form className="auth-form" noValidate aria-busy={sending} onSubmit={handleSubmit} onInput={() => { setErrors({}); setError('') }}>
        <FormField id="new-password" name="password" label={t("New password")} type="password" placeholder={t("At least 8 characters")}
          autoComplete="new-password" required disabled={sending} error={t(errors.password)} hint={t("Use at least 8 characters and no more than 72 UTF-8 bytes.")} />
        <FormField id="confirm-new-password" name="confirmation" label={t("Confirm password")} type="password" placeholder={t("Repeat your new password")}
          autoComplete="new-password" required disabled={sending} error={t(errors.confirmation)} />
        {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
        <button className="button button-primary button-full" type="submit" disabled={sending}>{sending ? t("Resetting password…") : t("Reset password")} <span aria-hidden="true">→</span></button>
      </form>}
    </section>
    <p className="auth-footer">{t("Need a reset link?")} <Link to="/forgot-password">{t("Forgot password")}</Link></p>
  </div>
}
