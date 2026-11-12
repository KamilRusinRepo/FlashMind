import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { registerAccount, RegistrationError, type RegisterResponse } from '../api/registration'
import FormField from '../components/FormField'

type AuthPageProps = { mode: 'login' | 'register' }
type FieldErrors = Partial<Record<'login' | 'username' | 'email' | 'password' | 'confirmation', string>>

export default function AuthPage({ mode }: AuthPageProps) {
  const isRegister = mode === 'register'
  const [errors, setErrors] = useState<FieldErrors>({})
  const [notice, setNotice] = useState('')
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [registeredAccount, setRegisteredAccount] = useState<RegisterResponse | null>(null)
  const submitting = useRef(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || registeredAccount) return
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
      if (!password) nextErrors.password = 'Enter your password.'
    }

    setErrors(nextErrors)
    setNotice('')
    setFormError('')
    const firstError = Object.keys(nextErrors)[0]
    if (firstError) {
      form.querySelector<HTMLInputElement>(`[name="${firstError}"]`)?.focus()
      return
    }

    if (!isRegister) {
      setNotice('Sign in is not available yet. You have not been signed in.')
      return
    }

    submitting.current = true
    setIsSubmitting(true)
    try {
      const account = await registerAccount({
        username: value('username').trim(),
        email: value('email').trim(),
        password,
      })
      form.reset()
      setRegisteredAccount(account)
    } catch (error) {
      if (error instanceof RegistrationError) {
        setErrors(error.fieldErrors)
        setFormError(error.message)
        const firstField = Object.keys(error.fieldErrors)[0]
        if (firstField) {
          requestAnimationFrame(() => form.querySelector<HTMLInputElement>(`[name="${firstField}"]`)?.focus())
        }
      } else {
        setFormError('Unable to create your account. Please try again.')
      }
    } finally {
      submitting.current = false
      setIsSubmitting(false)
    }
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
              <p>Open the link in your confirmation email to verify your address. Sign in will be available soon.</p>
            </div>
            <Link className="button button-primary button-full" to="/">Back to home</Link>
          </div>
        ) : (
        <form className="auth-form" noValidate aria-busy={isSubmitting} onSubmit={handleSubmit}
          onInput={() => { setNotice(''); setFormError('') }}>
          {isRegister ? (
            <>
              <FormField id="username" name="username" label="Username" placeholder="Your username"
                autoComplete="username" maxLength={50} disabled={isSubmitting} required error={errors.username} />
              <FormField id="email" name="email" label="Email" type="email" placeholder="name@example.com"
                autoComplete="email" maxLength={255} disabled={isSubmitting} required error={errors.email} />
            </>
          ) : (
            <FormField id="login" name="login" label="Email or username" placeholder="name@example.com"
              autoComplete="username" maxLength={255} required error={errors.login} />
          )}
          <FormField id="password" name="password" label="Password" type="password"
            placeholder={isRegister ? 'At least 8 characters' : 'Enter your password'}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            disabled={isSubmitting} required error={errors.password} />
          {isRegister && (
            <FormField id="confirmation" name="confirmation" label="Confirm password" type="password"
              placeholder="Repeat your password" autoComplete="new-password" disabled={isSubmitting} required error={errors.confirmation} />
          )}
          {Object.keys(errors).length > 0 && <p role="alert" className="field-error">Please check the highlighted fields.</p>}
          {formError && <p className="form-notice form-error" role="alert">{formError}</p>}
          {notice && <p className="form-notice" role="status">{notice}</p>}
          <button type="submit" className="button button-primary button-full" disabled={isSubmitting}>
            {isSubmitting ? 'Creating account…' : isRegister ? 'Create account' : 'Sign In'} <span aria-hidden="true">→</span>
          </button>
        </form>
        )}
        {!isRegister && <p className="availability-note">Sign in is coming soon. Your details are not sent or saved.</p>}
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
