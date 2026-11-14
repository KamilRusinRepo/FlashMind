import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import FormField from '../FormField'

export default function SettingsPasswordDialog({ onClose }: { onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    dialog?.querySelector<HTMLInputElement>('input')?.focus()
    return () => { if (dialog?.open) dialog.close() }
  }, [])

  function previewPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    event.currentTarget.reset()
    setNotice('Your password has not been changed. Saving will be available when account settings are connected.')
  }

  return <dialog ref={dialogRef} className="settings-password-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); onClose() }}>
    <div className="settings-dialog-heading"><h2 id={titleId}>Change Password</h2>
      <button className="settings-dialog-close" type="button" aria-label="Close change password" onClick={onClose}>×</button></div>
    <p className="settings-preview-note" id={descriptionId}>Form preview. Your password will not be changed.</p>
    <form className="settings-password-form" onSubmit={previewPassword}>
      <FormField id="settings-current-password" label="Current Password" name="currentPassword" type="password" autoComplete="current-password" required />
      <FormField id="settings-new-password" label="New Password" name="newPassword" type="password" autoComplete="new-password" minLength={8} required />
      <FormField id="settings-confirm-password" label="Confirm New Password" name="confirmPassword" type="password" autoComplete="new-password" minLength={8} required />
      {notice && <p className="settings-preview-note" role="status">{notice}</p>}
      <div className="settings-dialog-actions"><button className="button button-outline" type="button" onClick={onClose}>Cancel</button>
        <button className="button button-primary" type="submit">Save Password</button></div>
    </form>
  </dialog>
}
