import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router'
import { registerAccount, RegistrationError, type RegisterResponse } from '../api/registration'
import { LoginError } from '../api/login'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import ResendVerificationForm from '../components/ResendVerificationForm'

type AuthPageProps = { mode: 'login' | 'register' }
type FieldErrors = Partial<Record<'login' | 'username' | 'email' | 'password' | 'confirmation', string>>

export default function AuthPage({ mode }: AuthPageProps) {
  const isRegister = mode === 'register'
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
      <div className="auth-heading"><span className="eyebrow">WELCOME BACK</span><h1 id="auth-title">Sign In</h1></div>
      {status === 'loading' && <p className="form-notice" role="status">Checking your session…</p>}
      {status === 'error' && <p className="field-hint">Use the retry button above to check your session again.</p>}
    </section></div>
  }

  return (
    <div className="auth-page">
      <Link to="/" className="back-link"><span aria-hidden="true">←</span> Back to home</Link>
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="auth-heading">
          <span className="eyebrow">{isRegister ? 'START YOUR JOURNEY' : 'WELCOME BACK'}</span>
          <h1 id="auth-title">{registeredAccount ? 'Account created' : isRegister ? 'Create account' : 'Sign In'}</h1>
          <p>{registeredAccount ? `Welcome, ${registeredAccount.username}.` : isRegister ? 'Make a little room for learning, every day.' : 'Pick up where you left off.'}</p>
        </div>
        {registeredAccount ? (
          <div className="registration-success">
            <div className="form-notice form-success" role="status">
              <p>Your account has been created for <strong>{registeredAccount.email}</strong>.</p>
              <p>Verification emails are processed in the background. Please check your inbox.</p>
              <p>Open the link in your confirmation email to verify your address before signing in.</p>
            </div>
            <ResendVerificationForm email={registeredAccount.email} initialCooldownSeconds={60} />
            <Link className="button button-outline button-full" to="/login">Go to sign in</Link>
            <Link className="button button-primary button-full" to="/">Back to home</Link>
          </div>
        ) : (
        <form className="auth-form" noValidate aria-busy={isSubmitting} onSubmit={handleSubmit}
          onInput={() => { setFormError(''); setVerificationEmail(null) }}>
          {isRegister ? (
            <>
              <FormField id="username" name="username" label="Username" placeholder="Your username"
                autoComplete="username" maxLength={50} disabled={isSubmitting} required error={errors.username} />
              <FormField id="email" name="email" label="Email" type="email" placeholder="name@example.com"
                autoComplete="email" maxLength={255} disabled={isSubmitting} required error={errors.email} />
            </>
          ) : (
            <FormField id="login" name="login" label="Email or username" placeholder="name@example.com"
              autoComplete="username" maxLength={255} disabled={isSubmitting} required error={errors.login} />
          )}
          <FormField id="password" name="password" label="Password" type="password"
            placeholder={isRegister ? 'At least 8 characters' : 'Enter your password'}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            disabled={isSubmitting} required error={errors.password} />
          {!isRegister && <Link className="forgot-password-link text-link" to="/forgot-password">Forgot password?</Link>}
          {isRegister && (
            <FormField id="confirmation" name="confirmation" label="Confirm password" type="password"
              placeholder="Repeat your password" autoComplete="new-password" disabled={isSubmitting} required error={errors.confirmation} />
          )}
          {Object.keys(errors).length > 0 && <p role="alert" className="field-error">Please check the highlighted fields.</p>}
          {formError && <p className="form-notice form-error" role="alert">{formError}</p>}
          <button type="submit" className="button button-primary button-full" disabled={isSubmitting}>
            {isSubmitting ? isRegister ? 'Creating account…' : 'Signing in…' : isRegister ? 'Create account' : 'Sign In'} <span aria-hidden="true">→</span>
          </button>
        </form>
        )}
        {!isRegister && verificationEmail && (
          <div className="login-verification-resend">
            <p className="field-hint">Send a new confirmation link to <strong>{verificationEmail}</strong>.</p>
            <ResendVerificationForm key={verificationEmail} email={verificationEmail} />
          </div>
        )}
        {!isRegister && (
          <>
            <div className="or-separator"><span>OR TAKE A LOOK AROUND</span></div>
            <Link className="button button-secondary button-full" to="/#preview">Try a sample flashcard</Link>
          </>
        )}
      </section>
      {!registeredAccount && <p className="auth-footer">
        {isRegister ? 'Already have an account? ' : "Don't have an account? "}
        <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Sign in' : 'Sign up for free'}</Link>
      </p>}
    </div>
  )
}
