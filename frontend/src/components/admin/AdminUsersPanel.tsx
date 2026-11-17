import { useEffect, useState, type FormEvent } from 'react'
import { AdminUsersApiError, getAdminUsers, type AdminUser, type AdminUserFilters, type AdminUserPage, type AdminUserSummary } from '../../api/adminUsers'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import FormField from '../FormField'
import SelectField from '../SelectField'
import AdminPreviewAction from './AdminPreviewAction'
import AdminUserStatusDialog from './AdminUserStatusDialog'
import editIcon from '../../assets/icons/admin/edit.svg'
import deleteIcon from '../../assets/icons/admin/delete.svg'
import blockIcon from '../../assets/icons/admin/block.svg'
import unblockIcon from '../../assets/icons/admin/unblock.svg'

const emptyFilters: AdminUserFilters = { search: '', role: '', status: '' }
type LoadState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: AdminUserPage }

export default function AdminUsersPanel({ onSummary }: { onSummary: (summary: AdminUserSummary) => void }) {
  const { t } = useLanguage()
  const [draft, setDraft] = useState(emptyFilters)
  const [filters, setFilters] = useState(emptyFilters)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState('')
  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setFilters({ ...draft, search: draft.search.trim() }); setPage(0); setNotice(''); setRevision(value => value + 1)
  }
  return <section className="admin-panel" aria-labelledby="admin-users-title">
    <div className="admin-panel-heading"><h2 id="admin-users-title">{t('Users')}</h2></div>
    {notice && <p className="form-notice form-success" role="status">{t(notice)}</p>}
    <form className="admin-filters" role="search" aria-label={t('Search users')} onSubmit={apply}>
      <FormField id="admin-user-search" label={t('Search users')} type="search" placeholder={t('Username or email…')} value={draft.search} maxLength={120} onChange={event => setDraft(value => ({ ...value, search: event.target.value }))} />
      <SelectField id="admin-user-role" label={t('Role')} value={draft.role} onChange={event => setDraft(value => ({ ...value, role: event.target.value }))}>
        <option value="">{t('All roles')}</option><option value="USER">USER</option><option value="ADMIN">ADMIN</option>
      </SelectField>
      <SelectField id="admin-user-status" label={t('Account status')} value={draft.status} onChange={event => setDraft(value => ({ ...value, status: event.target.value }))}>
        <option value="">{t('All statuses')}</option><option value="active">{t('Active')}</option><option value="blocked">{t('Blocked')}</option>
      </SelectField>
      <div className="admin-filter-actions"><button className="button button-primary" type="submit">{t('Search')}</button>
        <button className="button button-outline" type="button" onClick={() => { setDraft(emptyFilters); setFilters(emptyFilters); setPage(0); setNotice(''); setRevision(value => value + 1) }}>{t('Clear filters')}</button></div>
    </form>
    <UsersResults key={JSON.stringify([filters, page, revision])} filters={filters} page={page} onPageChange={setPage} onSummary={onSummary}
      onSaved={blocked => { setNotice(blocked ? 'The account has been blocked.' : 'The account has been unblocked.'); setRevision(value => value + 1) }} />
  </section>
}

function UsersResults({ filters, page, onPageChange, onSummary, onSaved }: {
  filters: AdminUserFilters; page: number; onPageChange: (page: number) => void; onSummary: (summary: AdminUserSummary) => void; onSaved: (blocked: boolean) => void
}) {
  const { t, locale } = useLanguage()
  const { user: currentUser, refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<AdminUser | null>(null)
  const { search, role, status } = filters
  useEffect(() => {
    const controller = new AbortController()
    getAdminUsers({ search, role, status }, page, controller.signal).then(data => {
      if (controller.signal.aborted) return
      onSummary(data.summary)
      if (page > 0 && data.content.length === 0) { onPageChange(Math.max(0, data.totalPages - 1)); return }
      setState({ status: 'ready', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof AdminUsersApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setState({ status: 'error', message: cause instanceof AdminUsersApiError ? cause.message : 'Unable to load users. Please try again.' })
    })
    return () => controller.abort()
  }, [search, role, status, page, attempt, refresh, onSummary, onPageChange])

  if (state.status === 'loading') return <p className="admin-empty" role="status">{t('Loading users…')}</p>
  if (state.status === 'error') return <div className="admin-empty"><p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button></div>
  const { data } = state
  return <>
    <p className="admin-result-count" role="status">{t('Results: {0}', [data.totalElements.toLocaleString(locale)])}</p>
    {data.content.length === 0 ? <div className="admin-empty"><h3>{t('No matching results')}</h3><p>{t('Try another search or clear your filters.')}</p></div>
      : <div className="admin-table-scroll" tabIndex={0} role="region" aria-label={t('Users table')}><table className="admin-table">
        <caption className="db-sr-only">{t('Users table')}</caption><thead><tr>{['User', 'Role', 'Account status', 'Email verification', 'Joined', 'Actions'].map(label => <th key={label} scope="col">{t(label)}</th>)}</tr></thead>
        <tbody>{data.content.map(user => <tr key={user.id}>
          <th scope="row"><div className="admin-user-cell"><span className="admin-avatar" aria-hidden="true">{user.username.slice(0, 2).toUpperCase()}</span><div><strong>{user.username}</strong><span>{user.email}</span></div></div></th>
          <td><div className="admin-role-list">{user.roles.length ? user.roles.map(role => <span key={role} className={`admin-badge${role === 'ADMIN' ? ' is-admin' : ''}`}>{role}</span>) : t('No roles')}</div></td>
          <td><span className={`admin-badge ${user.enabled ? 'is-active' : 'is-blocked'}`}>{user.enabled ? t('Active') : t('Blocked')}</span></td>
          <td>{user.emailVerified ? t('Verified') : t('Not verified')}</td><td><time dateTime={user.createdAt}>{new Date(user.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })}</time></td>
          <td><div className="admin-row-actions"><AdminPreviewAction icon={editIcon} label={t('Edit user {0}', [user.username])} />
            <button className="admin-action-button" type="button" disabled={user.id === currentUser?.id} onClick={() => setSelected(user)}
              aria-label={t(user.enabled ? 'Block {0}' : 'Unblock {0}', [user.username])} title={user.id === currentUser?.id ? t('You cannot block your own account.') : t(user.enabled ? 'Block {0}' : 'Unblock {0}', [user.username])}>
              <span className="admin-action-icon" aria-hidden="true" style={{ maskImage: `url("${user.enabled ? blockIcon : unblockIcon}")` }} />
            </button><AdminPreviewAction icon={deleteIcon} label={t('Delete user {0}', [user.username])} danger /></div></td>
        </tr>)}</tbody>
      </table></div>}
    {data.totalPages > 1 && <nav className="admin-pagination" aria-label={t('User pagination')}>
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>{t('← Previous')}</button>
      <span>{t('Page {0} of {1}', [(page + 1).toLocaleString(locale), data.totalPages.toLocaleString(locale)])}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => onPageChange(page + 1)}>{t('Next →')}</button>
    </nav>}
    {selected && <AdminUserStatusDialog user={selected} onCancel={() => setSelected(null)} onSaved={() => onSaved(selected.enabled)} />}
  </>
}
