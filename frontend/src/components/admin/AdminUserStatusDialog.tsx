import { useEffect, useId, useRef, useState } from 'react'
import { AdminUsersApiError, setAdminUserEnabled, type AdminUser } from '../../api/adminUsers'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'

export default function AdminUserStatusDialog({ user, onCancel, onSaved }: { user: AdminUser; onCancel: () => void; onSaved: () => void }) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const submitting = useRef(false)
  const mounted = useRef(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    mounted.current = true
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { mounted.current = false; if (dialog?.open) dialog.close() }
  }, [])

  async function save() {
    if (submitting.current) return
    submitting.current = true; setSending(true); setError('')
    try {
      await setAdminUserEnabled(user.id, !user.enabled)
      if (mounted.current) onSaved()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof AdminUsersApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setError(cause instanceof AdminUsersApiError ? cause.message : 'Unable to change account status. Please try again.')
    } finally { submitting.current = false; if (mounted.current) setSending(false) }
  }
  return <dialog ref={dialogRef} className="admin-status-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!submitting.current) onCancel() }}>
    <h2 id={titleId}>{t(user.enabled ? 'Block account?' : 'Unblock account?')}</h2>
    <p id={descriptionId}>{t(user.enabled ? 'Block account {0}? This will sign the user out and prevent sign-in.' : 'Unblock account {0}? The user will be able to sign in again.', [user.username])}</p>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <div className="admin-dialog-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!submitting.current) onCancel() }}>{t('Cancel')}</button>
      <button className={`button ${user.enabled ? 'deck-delete-button' : 'button-primary'}`} type="button" disabled={sending} onClick={() => void save()}>{sending ? t('Saving…') : user.enabled ? t('Block') : t('Unblock')}</button>
    </div>
  </dialog>
}
