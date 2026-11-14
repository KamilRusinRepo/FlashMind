import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { EmailChangeError, getPendingEmailChange, requestEmailChange, type PendingEmailChange } from '../../api/emailChange'
import { useAuth } from '../../auth/AuthContext'
import FormField from '../FormField'

export default function SettingsEmailDialog({ onClose }: { onClose: () => void }) {
  const { refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const [email, setEmail] = useState('')
  const [pending, setPending] = useState<PendingEmailChange | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [sending, setSending] = useState(false)
  const [remaining, setRemaining] = useState(0)
  const deadline = useRef(0)
  const busy = useRef(false)
  const mounted = useRef(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [fields, setFields] = useState<EmailChangeError['fieldErrors']>({})

  useEffect(() => {
    mounted.current = true
    const dialog = dialogRef.current
    dialog?.showModal()
    const timer = window.setInterval(() => setRemaining(Math.max(0, Math.ceil((deadline.current - Date.now()) / 1000))), 1000)
    return () => { mounted.current = false; window.clearInterval(timer); if (dialog?.open) dialog.close() }
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    getPendingEmailChange(controller.signal).then(data => {
      if (controller.signal.aborted) return
      setPending(data); setEmail(data.pendingEmail ?? '')
      deadline.current = Date.now() + data.retryAfterSeconds * 1000
      setRemaining(data.retryAfterSeconds); setLoading(false)
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof EmailChangeError && cause.status === 401) { refresh(); return }
      setError(cause instanceof Error ? cause.message : 'Unable to check pending changes.'); setLoadFailed(true); setLoading(false)
    })
    return () => controller.abort()
  }, [attempt, refresh])
  useEffect(() => { if (!loading) dialogRef.current?.querySelector<HTMLInputElement>('input')?.focus() }, [loading])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current || loading || loadFailed || Date.now() < deadline.current) return
    const form = event.currentTarget
    const password = String(new FormData(form).get('currentPassword') ?? '')
    busy.current = true; setSending(true); setError(''); setFields({}); setNotice('')
    try {
      const data = await requestEmailChange(email, password)
      if (!mounted.current) return
      setPending(data); setEmail(data.pendingEmail ?? '')
      deadline.current = Date.now() + data.retryAfterSeconds * 1000; setRemaining(data.retryAfterSeconds)
      form.reset()
      setNotice('Email change requested. Check your new inbox for the confirmation link. Your current address is still active. If the message does not arrive, request another confirmation after the cooldown.')
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof EmailChangeError && cause.status === 401) { refresh(); return }
      setError(cause instanceof Error ? cause.message : 'Unable to request an email change.')
      if (cause instanceof EmailChangeError) {
        setFields(cause.fieldErrors)
        if (cause.status === 429) { deadline.current = Date.now() + 60000; setRemaining(60) }
      }
    } finally {
      const passwordInput = form.querySelector<HTMLInputElement>('[name="currentPassword"]')
      if (passwordInput) passwordInput.value = ''
      busy.current = false; if (mounted.current) setSending(false)
    }
  }

  return <dialog ref={dialogRef} className="settings-password-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={loading || sending}
    onCancel={event => { event.preventDefault(); if (!busy.current) onClose() }}>
    <div className="settings-dialog-heading"><h2 id={titleId}>Change Email Address</h2>
      <button className="settings-dialog-close" type="button" disabled={sending} aria-label="Close email change" onClick={onClose}>×</button></div>
    <p className="settings-preview-note" id={descriptionId}>Confirm the link sent to your new address. Until then, your current address remains active. After confirmation, you will need to sign in again.</p>
    {loading && <p role="status">Checking pending changes…</p>}
    {pending?.pendingEmail && <p className="settings-preview-note">Awaiting confirmation: <strong>{pending.pendingEmail}</strong>. Requesting another link replaces the previous one.</p>}
    {error && <p className="form-notice form-error" role="alert">{error}</p>}
    {loadFailed && <button className="button button-outline" type="button" onClick={() => { setLoading(true); setLoadFailed(false); setError(''); setAttempt(value => value + 1) }}>Try again</button>}
    <form className="settings-password-form" onSubmit={submit}>
      <FormField id="settings-new-email" label="New Email Address" name="email" type="email" autoComplete="email" required maxLength={255} value={email} onChange={event => setEmail(event.target.value)} error={fields.email} disabled={loading || sending || loadFailed} />
      <FormField id="settings-email-current-password" label="Current Password" name="currentPassword" type="password" autoComplete="current-password" required error={fields.currentPassword} disabled={loading || sending || loadFailed} />
      {notice && <p className="form-notice form-success" role="status">{notice}</p>}
      <div className="settings-dialog-actions"><button className="button button-outline" type="button" disabled={sending} onClick={onClose}>Cancel</button>
        <button className="button button-primary" type="submit" disabled={loading || sending || loadFailed || remaining > 0}>{sending ? 'Requesting…' : remaining > 0 ? `Wait ${remaining}s` : 'Send confirmation'}</button></div>
    </form>
  </dialog>
}
