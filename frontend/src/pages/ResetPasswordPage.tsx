import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import FormField from '../components/FormField'

type PasswordErrors = Partial<Record<'password' | 'confirmation', string>>

export default function ResetPasswordPage() {
  const [errors, setErrors] = useState<PasswordErrors>({})
  const [notice, setNotice] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const password = String(data.get('password') ?? '')
    const confirmation = String(data.get('confirmation') ?? '')
    const nextErrors: PasswordErrors = {}
    if (!password.trim() || password.length < 8) nextErrors.password = 'Use at least 8 characters.'
    else if (new TextEncoder().encode(password).length > 72) nextErrors.password = 'Password must not exceed 72 UTF-8 bytes.'
    if (!confirmation || password !== confirmation) nextErrors.confirmation = 'Passwords must match.'
    setErrors(nextErrors)
    setNotice('')
    const firstError = Object.keys(nextErrors)[0]
    if (firstError) {
      form.querySelector<HTMLInputElement>(`[name="${firstError}"]`)?.focus()
      return
    }
    setNotice('Password reset is not available yet. Your password has not been changed.')
  }

  return <div className="auth-page">
    <Link to="/login" className="back-link"><span aria-hidden="true">←</span> Back to sign in</Link>
    <section className="auth-card" aria-labelledby="reset-password-title">
      <div className="auth-heading">
        <span className="eyebrow">A FRESH START</span>
        <h1 id="reset-password-title">Set a new password</h1>
        <p>Choose a new password and enter it again to confirm.</p>
      </div>
      <form className="auth-form" noValidate onSubmit={handleSubmit} onInput={() => { setErrors({}); setNotice('') }}>
        <FormField id="new-password" name="password" label="New password" type="password" placeholder="At least 8 characters"
          autoComplete="new-password" required error={errors.password} hint="Use at least 8 characters and no more than 72 UTF-8 bytes." />
        <FormField id="confirm-new-password" name="confirmation" label="Confirm password" type="password" placeholder="Repeat your new password"
          autoComplete="new-password" required error={errors.confirmation} />
        <p className="form-notice">Password reset is coming soon. This form does not change your password yet.</p>
        {notice && <p className="form-notice" role="status">{notice}</p>}
        <button className="button button-primary button-full" type="submit">Reset password <span aria-hidden="true">→</span></button>
      </form>
    </section>
    <p className="auth-footer">Need a reset link? <Link to="/forgot-password">Forgot password</Link></p>
  </div>
}
