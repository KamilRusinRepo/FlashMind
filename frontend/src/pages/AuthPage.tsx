import { useLanguage } from '../i18n/LanguageContext'
import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { registerAccount, RegistrationError, type RegisterResponse } from '../api/registration'
import { LoginError } from '../api/login'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import ResendVerificationForm from '../components/ResendVerificationForm'

type AuthPageProps = { mode: 'login' | 'register' }
type FieldErrors = Partial<Record<'login' | 'username' | 'email' | 'password' | 'confirmation', string>>

export default function AuthPage({ mode }: AuthPageProps) {
  const { t } = useLanguage()
  const isRegister = mode === 'register'
  const location = useLocation()
  const { user, status, signIn } = useAuth()
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [registeredAccount, setRegisteredAccount] = useState<RegisterResponse | null>(null)
  const [verificationEmail, setVerificationEmail] = useState<string | null>(null)
  const submitting = useRef(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || registeredAccount || (!isRegister && user)) return
    const form = event.currentTarget
    const data = new FormData(form)
    const value = (name: string) => String(data.get(name) ?? '')
    const nextErrors: FieldErrors = {}
    const password = value('password')

    if (isRegister) {
      const username = value('username').trim()
      if (username.length < 3 || username.length > 50) {
        nextErrors.username = 'Use between 3 and 50 characters.'
      } else if (!/^[a-zA-Z0-9_.-]+$/.test(username)) {
        nextErrors.username = 'Use letters, digits, dots, hyphens or underscores.'
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value('email').trim())) {
        nextErrors.email = 'Enter a valid email address.'
      }
      if (!password.trim() || password.length < 8) {
        nextErrors.password = 'Use at least 8 characters.'
      } else if (new TextEncoder().encode(password).length > 72) {
        nextErrors.password = 'Password must not exceed 72 UTF-8 bytes.'
      }
      if (!value('confirmation') || value('confirmation') !== password) {
        nextErrors.confirmation = 'Passwords must match.'
      }
    } else {
      if (!value('login').trim()) nextErrors.login = 'Enter your email or username.'
      else if (value('login').trim().length > 255) nextErrors.login = 'Use at most 255 characters.'
      if (!password) nextErrors.password = 'Enter your password.'
      else if (new TextEncoder().encode(password).length > 72) nextErrors.password = 'Password must not exceed 72 UTF-8 bytes.'
    }

    setErrors(nextErrors)
    setFormError('')
    setVerificationEmail(null)
    const firstError = Object.keys(nextErrors)[0]
    if (firstError) {
      form.querySelector<HTMLInputElement>(`[name="${firstError}"]`)?.focus()
      return
    }

    submitting.current = true
    setIsSubmitting(true)
    try {
      if (isRegister) {
        const account = await registerAccount({ username: value('username').trim(), email: value('email').trim(), password })
        setRegisteredAccount(account)
      } else {
        await signIn(value('login').trim(), password)
      }
      form.reset()
    } catch (error) {
      if (error instanceof RegistrationError || error instanceof LoginError) {
        setErrors(error.fieldErrors)
        setFormError(error.message)
        if (!isRegister && error instanceof LoginError && error.verificationEmail) {
          setVerificationEmail(error.verificationEmail)
        }
        const firstField = Object.keys(error.fieldErrors)[0]
        if (firstField) {
          requestAnimationFrame(() => form.querySelector<HTMLInputElement>(`[name="${firstField}"]`)?.focus())
        }
      } else {
        setFormError(isRegister ? 'Unable to create your account. Please try again.' : 'Unable to sign in. Please try again.')
      }
    } finally {
      submitting.current = false
      setIsSubmitting(false)
    }
  }

  if (!isRegister && status === 'authenticated' && user) {
    return <Navigate to="/dashboard" replace />
  }

  if (!isRegister && (status === 'loading' || status === 'error')) {
    return <div className="auth-page"><section className="auth-card" aria-labelledby="auth-title">
      <div className="auth-heading"><span className="eyebrow">{t("WELCOME BACK")}</span><h1 id="auth-title">{t("Sign In")}</h1></div>
      {status === 'loading' && <p className="form-notice" role="status">{t("Checking your session…")}</p>}
      {status === 'error' && <p className="field-hint">{t("Use the retry button above to check your session again.")}</p>}
    </section></div>
  }

  return (
    <div className="auth-page">
      <Link to="/" className="back-link"><span aria-hidden="true">←</span> {t("Back to home")}</Link>
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="auth-heading">
          <span className="eyebrow">{isRegister ? t("START YOUR JOURNEY") : t("WELCOME BACK")}</span>
          <h1 id="auth-title">{registeredAccount ? t("Account created") : isRegister ? t("Create account") : t("Sign In")}</h1>
          <p>{registeredAccount ? t("Welcome, {0}.", [registeredAccount.username]) : isRegister ? t("Make a little room for learning, every day.") : t("Pick up where you left off.")}</p>
        </div>
        {registeredAccount ? (
          <div className="registration-success">
            <div className="form-notice form-success" role="status">
              <p>{t("Your account has been created for")} <strong>{registeredAccount.email}</strong>.</p>
              <p>{t("Verification emails are processed in the background. Please check your inbox.")}</p>
              <p>{t("Open the link in your confirmation email to verify your address before signing in.")}</p>
            </div>
            <ResendVerificationForm email={registeredAccount.email} initialCooldownSeconds={60} />
            <Link className="button button-outline button-full" to="/login">{t("Go to sign in")}</Link>
            <Link className="button button-primary button-full" to="/">{t("Back to home")}</Link>
          </div>
        ) : (
        <form className="auth-form" noValidate aria-busy={isSubmitting} onSubmit={handleSubmit}
          onInput={() => { setFormError(''); setVerificationEmail(null) }}>
          {!isRegister && location.state?.passwordChanged === true && <p className="form-notice form-success" role="status">{t("Your password has been changed. Sign in with your new password.")}</p>}
          {isRegister ? (
            <>
              <FormField id="username" name="username" label={t("Username")} placeholder={t("Your username")}
                autoComplete="username" maxLength={50} disabled={isSubmitting} required error={t(errors.username)} />
              <FormField id="email" name="email" label={t("Email")} type="email" placeholder={t("name@example.com")}
                autoComplete="email" maxLength={255} disabled={isSubmitting} required error={t(errors.email)} />
            </>
          ) : (
            <FormField id="login" name="login" label={t("Email or username")} placeholder={t("name@example.com")}
              autoComplete="username" maxLength={255} disabled={isSubmitting} required error={t(errors.login)} />
          )}
          <FormField id="password" name="password" label={t("Password")} type="password"
            placeholder={isRegister ? t("At least 8 characters") : t("Enter your password")}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            disabled={isSubmitting} required error={t(errors.password)} />
          {!isRegister && <Link className="forgot-password-link text-link" to="/forgot-password">{t("Forgot password?")}</Link>}
          {isRegister && (
            <FormField id="confirmation" name="confirmation" label={t("Confirm password")} type="password"
              placeholder={t("Repeat your password")} autoComplete="new-password" disabled={isSubmitting} required error={t(errors.confirmation)} />
          )}
          {Object.keys(errors).length > 0 && <p role="alert" className="field-error">{t("Please check the highlighted fields.")}</p>}
          {formError && <p className="form-notice form-error" role="alert">{t(formError)}</p>}
          <button type="submit" className="button button-primary button-full" disabled={isSubmitting}>
            {isSubmitting ? isRegister ? t("Creating account…") : t("Signing in…") : isRegister ? t("Create account") : t("Sign In")} <span aria-hidden="true">→</span>
          </button>
        </form>
        )}
        {!isRegister && verificationEmail && (
          <div className="login-verification-resend">
            <p className="field-hint">{t("Send a new confirmation link to")} <strong>{verificationEmail}</strong>.</p>
            <ResendVerificationForm key={verificationEmail} email={verificationEmail} />
          </div>
        )}
        {!isRegister && (
          <>
            <div className="or-separator"><span>{t("OR TAKE A LOOK AROUND")}</span></div>
            <Link className="button button-secondary button-full" to="/#preview">{t("Try a sample flashcard")}</Link>
          </>
        )}
      </section>
      {!registeredAccount && <p className="auth-footer">
        {isRegister ? t("Already have an account? ") : t("Don't have an account? ")}
        <Link to={isRegister ? '/login' : '/register'}>{isRegister ? t("Sign in") : t("Sign up for free")}</Link>
      </p>}
    </div>
  )
}
