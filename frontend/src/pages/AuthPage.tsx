import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import FormField from '../components/FormField'

type AuthPageProps = { mode: 'login' | 'register' }
type FieldErrors = Partial<Record<'login' | 'username' | 'email' | 'password' | 'confirmation', string>>

export default function AuthPage({ mode }: AuthPageProps) {
  const isRegister = mode === 'register'
  const [errors, setErrors] = useState<FieldErrors>({})
  const [notice, setNotice] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const value = (name: string) => String(data.get(name) ?? '')
    const nextErrors: FieldErrors = {}
    const password = value('password')

    if (isRegister) {
      const username = value('username').trim()
      if (username.length < 3 || username.length > 50) {
        nextErrors.username = 'Use between 3 and 50 characters.'
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value('email').trim())) {
        nextErrors.email = 'Enter a valid email address.'
      }
      if (password.length < 8) {
        nextErrors.password = 'Use at least 8 characters.'
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
    const firstError = Object.keys(nextErrors)[0]
    if (firstError) {
      form.querySelector<HTMLInputElement>(`[name="${firstError}"]`)?.focus()
      return
    }

    setNotice(isRegister
      ? 'Account registration is not available yet. No account has been created.'
      : 'Sign in is not available yet. You have not been signed in.')
  }

  return (
    <div className="auth-page">
      <Link to="/" className="back-link"><span aria-hidden="true">←</span> Back to home</Link>
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="auth-heading">
          <span className="eyebrow">{isRegister ? 'START YOUR JOURNEY' : 'WELCOME BACK'}</span>
          <h1 id="auth-title">{isRegister ? 'Create account' : 'Sign In'}</h1>
          <p>{isRegister ? 'Make a little room for learning, every day.' : 'Pick up where you left off.'}</p>
        </div>
        <form className="auth-form" noValidate onSubmit={handleSubmit} onInput={() => notice && setNotice('')}>
          {isRegister ? (
            <>
              <FormField id="username" name="username" label="Username" placeholder="Your username"
                autoComplete="username" maxLength={50} required error={errors.username} />
              <FormField id="email" name="email" label="Email" type="email" placeholder="name@example.com"
                autoComplete="email" maxLength={255} required error={errors.email} />
            </>
          ) : (
            <FormField id="login" name="login" label="Email or username" placeholder="name@example.com"
              autoComplete="username" maxLength={255} required error={errors.login} />
          )}
          <FormField id="password" name="password" label="Password" type="password"
            placeholder={isRegister ? 'At least 8 characters' : 'Enter your password'}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            required error={errors.password} />
          {isRegister && (
            <FormField id="confirmation" name="confirmation" label="Confirm password" type="password"
              placeholder="Repeat your password" autoComplete="new-password" required error={errors.confirmation} />
          )}
          {Object.keys(errors).length > 0 && <p role="alert" className="field-error">Please check the highlighted fields.</p>}
          {notice && <p className="form-notice" role="status">{notice}</p>}
          <button type="submit" className="button button-primary button-full">
            {isRegister ? 'Create account' : 'Sign In'} <span aria-hidden="true">→</span>
          </button>
        </form>
        <p className="availability-note">Account access is coming soon. Your details are not sent or saved.</p>
        {!isRegister && (
          <>
            <div className="or-separator"><span>OR TAKE A LOOK AROUND</span></div>
            <Link className="button button-secondary button-full" to="/#preview">Try a sample flashcard</Link>
          </>
        )}
      </section>
      <p className="auth-footer">
        {isRegister ? 'Already have an account? ' : "Don't have an account? "}
        <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Sign in' : 'Sign up for free'}</Link>
      </p>
    </div>
  )
}
