import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { changePassword, PasswordChangeError, type PasswordChangeFields, type PasswordChangeRequest } from '../../api/passwordChange'
import { useAuth } from '../../auth/AuthContext'
import FormField from '../FormField'

export default function SettingsPasswordDialog({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const [error, setError] = useState('')
  const [fields, setFields] = useState<PasswordChangeFields>({})
  const [sending, setSending] = useState(false)
  const busy = useRef(false)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    const dialog = dialogRef.current
    dialog?.showModal()
    dialog?.querySelector<HTMLInputElement>('input')?.focus()
    return () => { mounted.current = false; if (dialog?.open) dialog.close() }
  }, [])

  async function savePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current) return
    const form = event.currentTarget
    const data = new FormData(form)
    const input: PasswordChangeRequest = {
      currentPassword: String(data.get('currentPassword') ?? ''),
      newPassword: String(data.get('newPassword') ?? ''),
      confirmPassword: String(data.get('confirmPassword') ?? ''),
    }
    const next: PasswordChangeFields = {}
    for (const field of ['currentPassword', 'newPassword', 'confirmPassword'] as const) {
      if (!input[field].trim()) next[field] = 'This field is required.'
      else if (new TextEncoder().encode(input[field]).length > 72) next[field] = 'Password must not exceed 72 UTF-8 bytes.'
    }
    if (!next.newPassword && input.newPassword.length < 8) next.newPassword = 'Use at least 8 characters.'
    if (!next.confirmPassword && input.newPassword !== input.confirmPassword) next.confirmPassword = 'Passwords must match.'
    setError(''); setFields(next)
    const firstError = Object.keys(next)[0]
    if (firstError) { form.querySelector<HTMLInputElement>(`[name="${firstError}"]`)?.focus(); return }
    busy.current = true; setSending(true)
    try {
      await changePassword(input)
      if (!mounted.current) return
      form.reset()
      navigate('/login', { replace: true, state: { passwordChanged: true } })
      refresh()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof PasswordChangeError && cause.status === 401) { refresh(); return }
      setError(cause instanceof Error ? cause.message : 'Unable to change your password.')
      if (cause instanceof PasswordChangeError) setFields(cause.fieldErrors)
      const first = cause instanceof PasswordChangeError ? Object.keys(cause.fieldErrors)[0] : undefined
      if (first) requestAnimationFrame(() => { if (mounted.current) form.querySelector<HTMLInputElement>(`[name="${first}"]`)?.focus() })
    } finally {
      busy.current = false; if (mounted.current) setSending(false)
    }
  }

  return <dialog ref={dialogRef} className="settings-password-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!busy.current) onClose() }}>
    <div className="settings-dialog-heading"><h2 id={titleId}>Change Password</h2>
      <button className="settings-dialog-close" type="button" disabled={sending} aria-label="Close change password" onClick={onClose}>×</button></div>
    <p className="settings-preview-note" id={descriptionId}>After changing your password, you will be signed out on all devices. Pending email changes and password reset links will be cancelled.</p>
    <form className="settings-password-form" onSubmit={savePassword} noValidate aria-busy={sending} onInput={() => { setFields({}); setError('') }}>
      <FormField id="settings-current-password" label="Current Password" name="currentPassword" type="password" autoComplete="current-password" required error={fields.currentPassword} disabled={sending} />
      <FormField id="settings-new-password" label="New Password" name="newPassword" type="password" autoComplete="new-password" required error={fields.newPassword} disabled={sending} hint="Use at least 8 characters and no more than 72 UTF-8 bytes." />
      <FormField id="settings-confirm-password" label="Confirm New Password" name="confirmPassword" type="password" autoComplete="new-password" required error={fields.confirmPassword} disabled={sending} />
      {error && <p className="form-notice form-error" role="alert">{error}</p>}
      <div className="settings-dialog-actions"><button className="button button-outline" type="button" disabled={sending} onClick={onClose}>Cancel</button>
        <button className="button button-primary" type="submit" disabled={sending}>{sending ? 'Saving…' : 'Save Password'}</button></div>
    </form>
  </dialog>
}
