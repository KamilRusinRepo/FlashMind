import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import FormField from '../components/FormField'

export default function ForgotPasswordPage() {
  const [emailError, setEmailError] = useState('')
  const [notice, setNotice] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const email = String(new FormData(form).get('email') ?? '').trim()
    const error = !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
      ? 'Enter a valid email address.' : email.length > 255 ? 'Use at most 255 characters.' : ''
    setEmailError(error)
    setNotice('')
    if (error) {
      form.querySelector<HTMLInputElement>('[name="email"]')?.focus()
      return
    }
    setNotice('Sending reset links is not available yet. No email has been sent.')
  }

  return <div className="auth-page">
    <Link to="/login" className="back-link"><span aria-hidden="true">←</span> Back to sign in</Link>
    <section className="auth-card" aria-labelledby="forgot-password-title">
      <div className="auth-heading">
        <span className="eyebrow">LET’S GET YOU BACK</span>
        <h1 id="forgot-password-title">Forgot password?</h1>
        <p>Enter your email address to request a password reset link.</p>
      </div>
      <form className="auth-form" noValidate onSubmit={handleSubmit} onInput={() => { setEmailError(''); setNotice('') }}>
        <FormField id="recovery-email" name="email" label="Email" type="email" placeholder="name@example.com"
          autoComplete="email" maxLength={255} required error={emailError} />
        <p className="form-notice">Password recovery is coming soon. This form does not send emails yet.</p>
        {notice && <p className="form-notice" role="status">{notice}</p>}
        <button className="button button-primary button-full" type="submit">Send reset link <span aria-hidden="true">→</span></button>
      </form>
    </section>
    <p className="auth-footer">Remember your password? <Link to="/login">Sign in</Link></p>
  </div>
}
