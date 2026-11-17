import { useEffect, useId, useRef, useState } from 'react'
import type { AdminUser } from '../../api/adminUsers'
import { useLanguage } from '../../i18n/LanguageContext'
import FormField from '../FormField'

export default function AdminEditUserDialog({ user, onCancel }: { user: AdminUser; onCancel: () => void }) {
  const { t } = useLanguage()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const id = useId()
  const [username, setUsername] = useState(user.username)
  const [email, setEmail] = useState(user.email)
  const [roles, setRoles] = useState(user.roles)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { if (dialog?.open) dialog.close() }
  }, [])

  function toggleRole(role: string, checked: boolean) {
    setRoles(current => checked ? [...current, role] : current.filter(value => value !== role))
  }

  return <dialog ref={dialogRef} className="admin-status-dialog admin-edit-user-dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-preview`}
    onCancel={event => { event.preventDefault(); onCancel() }}>
    <h2 id={`${id}-title`}>{t('Edit user {0}', [user.username])}</h2>
    <p className="admin-edit-preview" id={`${id}-preview`}>{t('This form is a preview. Changes are not sent or saved.')}</p>
    <form className="admin-edit-user-form" onSubmit={event => event.preventDefault()}>
      <FormField id={`${id}-username`} name="username" label={t('Username')} value={username} required minLength={3} maxLength={50}
        autoComplete="off" spellCheck={false} onChange={event => setUsername(event.target.value)} />
      <FormField id={`${id}-email`} name="email" type="email" label={t('Email Address')} value={email} required maxLength={255}
        autoComplete="off" spellCheck={false} onChange={event => setEmail(event.target.value)} />
      <fieldset className="admin-edit-roles" aria-describedby={`${id}-roles-hint`}>
        <legend>{t('Roles')}</legend>
        <p className="field-hint" id={`${id}-roles-hint`}>{t('Choose at least one role for this account.')}</p>
        <div className="admin-edit-role-options">{['USER', 'ADMIN'].map(role => <label className="admin-edit-role-option" key={role}>
          <input type="checkbox" name="roles" value={role} checked={roles.includes(role)} onChange={event => toggleRole(role, event.target.checked)} />
          <span>{role}</span>
        </label>)}</div>
      </fieldset>
      <div className="admin-dialog-actions">
        <button className="button button-outline" type="button" onClick={onCancel}>{t('Cancel')}</button>
        <button className="button button-primary" type="submit" disabled title={t('Saving user edits is coming soon.')}>{t('Save changes')}</button>
      </div>
    </form>
  </dialog>
}
