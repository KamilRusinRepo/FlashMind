import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { AdminUsersApiError, updateAdminUser, type AdminUser, type AdminUserEditErrors, type AdminUserEditResponse } from '../../api/adminUsers'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import FormField from '../FormField'

export default function AdminEditUserDialog({ user, onCancel, onSaved }: { user: AdminUser; onCancel: () => void; onSaved: (result: AdminUserEditResponse) => void }) {
  const { t } = useLanguage()
  const { user: currentUser, refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const id = useId()
  const [username, setUsername] = useState(user.username)
  const [email, setEmail] = useState(user.email)
  const [roles, setRoles] = useState(user.roles)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<AdminUserEditErrors>({})
  const submitting = useRef(false)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { mounted.current = false; if (dialog?.open) dialog.close() }
  }, [])

  function toggleRole(role: string, checked: boolean) {
    setRoles(current => checked ? [...current, role] : current.filter(value => value !== role))
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    const fields: AdminUserEditErrors = {}
    if (username.trim().length < 3 || username.trim().length > 50) fields.username = 'Username must contain between 3 and 50 characters.'
    else if (!/^[a-zA-Z0-9_.-]+$/.test(username.trim())) fields.username = 'Username may contain letters, digits, dots, hyphens and underscores.'
    const emailInput = event.currentTarget.elements.namedItem('email')
    if (!email.trim()) fields.email = 'Email is required.'
    else if (email.trim().length > 255) fields.email = 'Email must not exceed 255 characters.'
    else if (emailInput instanceof HTMLInputElement && emailInput.validity.typeMismatch) fields.email = 'Enter a valid email address.'
    if (roles.length === 0) fields.roles = 'Choose at least one role for this account.'
    setFieldErrors(fields); setError('')
    if (Object.keys(fields).length) return
    submitting.current = true; setSending(true)
    try {
      const result = await updateAdminUser(user.id, { username, email, roles })
      if (!mounted.current) return
      if (currentUser?.id === user.id && result.sessionsInvalidated) { refresh(); return }
      onSaved(result)
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof AdminUsersApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setError(cause instanceof AdminUsersApiError ? cause.message : 'Unable to save user changes. Please try again.')
      if (cause instanceof AdminUsersApiError) setFieldErrors(cause.fieldErrors)
    } finally { submitting.current = false; if (mounted.current) setSending(false) }
  }

  return <dialog ref={dialogRef} className="admin-status-dialog admin-edit-user-dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-preview`}
    aria-busy={sending} onCancel={event => { event.preventDefault(); if (!submitting.current) onCancel() }}>
    <h2 id={`${id}-title`}>{t('Edit user {0}', [user.username])}</h2>
    <p className="admin-edit-preview" id={`${id}-preview`}>{t('Username and role changes sign the user out. A new email address requires confirmation; the current address stays active until then.')}</p>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <form className="admin-edit-user-form" noValidate onSubmit={event => void save(event)}>
      <FormField id={`${id}-username`} name="username" label={t('Username')} value={username} required minLength={3} maxLength={50}
        autoComplete="off" spellCheck={false} disabled={sending} error={fieldErrors.username} onChange={event => setUsername(event.target.value)} />
      <FormField id={`${id}-email`} name="email" type="email" label={t('Email Address')} value={email} required maxLength={255}
        autoComplete="off" spellCheck={false} disabled={sending} error={fieldErrors.email} onChange={event => setEmail(event.target.value)} />
      <fieldset className="admin-edit-roles" disabled={sending} aria-invalid={!!fieldErrors.roles} aria-describedby={`${id}-roles-hint${fieldErrors.roles ? ` ${id}-roles-error` : ''}`}>
        <legend>{t('Roles')}</legend>
        <p className="field-hint" id={`${id}-roles-hint`}>{t('Choose at least one role for this account.')}</p>
        <div className="admin-edit-role-options">{['USER', 'ADMIN'].map(role => <label className="admin-edit-role-option" key={role}>
          <input type="checkbox" name="roles" value={role} checked={roles.includes(role)} onChange={event => toggleRole(role, event.target.checked)} />
          <span>{role}</span>
        </label>)}</div>
        {fieldErrors.roles && <p className="field-error" id={`${id}-roles-error`}>{t(fieldErrors.roles)}</p>}
      </fieldset>
      <div className="admin-dialog-actions">
        <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!submitting.current) onCancel() }}>{t('Cancel')}</button>
        <button className="button button-primary" type="submit" disabled={sending}>{sending ? t('Saving…') : t('Save changes')}</button>
      </div>
    </form>
  </dialog>
}
