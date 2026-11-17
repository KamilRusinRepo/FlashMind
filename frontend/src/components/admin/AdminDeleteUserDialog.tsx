import { useEffect, useId, useRef, useState } from 'react'
import { AdminUsersApiError, deleteAdminUser, type AdminUser } from '../../api/adminUsers'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'

export default function AdminDeleteUserDialog({ user, onCancel, onDeleted, onUnavailable }: {
  user: AdminUser; onCancel: () => void; onDeleted: () => void; onUnavailable: () => void
}) {
  const { t } = useLanguage()
  const { user: currentUser, refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const id = useId()
  const submitting = useRef(false)
  const mounted = useRef(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [unavailable, setUnavailable] = useState(false)
  useEffect(() => {
    mounted.current = true
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { mounted.current = false; if (dialog?.open) dialog.close() }
  }, [])

  async function remove() {
    if (submitting.current || unavailable) return
    submitting.current = true; setSending(true); setError('')
    try {
      await deleteAdminUser(user.id)
      if (!mounted.current) return
      if (user.id === currentUser?.id) { refresh(); return }
      onDeleted()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof AdminUsersApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setError(cause instanceof AdminUsersApiError ? cause.message : 'Unable to delete this user. Please try again.')
      setUnavailable(cause instanceof AdminUsersApiError && cause.status === 404)
    } finally { submitting.current = false; if (mounted.current) setSending(false) }
  }

  return <dialog ref={dialogRef} className="admin-status-dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!submitting.current) onCancel() }}>
    <h2 id={`${id}-title`}>{t('Delete user?')}</h2>
    <p id={`${id}-description`}>{t('Delete user {0}? The account, private decks and personal study data will be permanently removed. Public decks will remain available as Deleted user.', [user.username])}</p>
    {currentUser?.id === user.id && <p>{t('You are deleting your own account. You will be signed out.')}</p>}
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <div className="admin-dialog-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!submitting.current) onCancel() }}>{t('Cancel')}</button>
      {unavailable ? <button className="button button-primary" type="button" onClick={onUnavailable}>{t('Refresh users')}</button>
        : <button className="button deck-delete-button" type="button" disabled={sending} onClick={() => void remove()}>{sending ? t('Deleting…') : t('Delete user')}</button>}
    </div>
  </dialog>
}
