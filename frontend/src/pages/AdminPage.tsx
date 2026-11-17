import { useState } from 'react'
import FormField from '../components/FormField'
import SelectField from '../components/SelectField'
import { adminPreviewDecks, adminPreviewUsers } from '../data/adminPreview'
import { useLanguage } from '../i18n/LanguageContext'
import editIcon from '../assets/icons/admin/edit.svg'
import deleteIcon from '../assets/icons/admin/delete.svg'
import blockIcon from '../assets/icons/admin/block.svg'
import unblockIcon from '../assets/icons/admin/unblock.svg'
import './AdminPage.css'

const pageSize = 5

export default function AdminPage() {
  const { t, locale } = useLanguage()
  const [section, setSection] = useState<'users' | 'decks'>('users')
  const number = new Intl.NumberFormat(locale)
  const metrics = [
    { label: 'Users', value: adminPreviewUsers.length },
    { label: 'Active accounts', value: adminPreviewUsers.filter(user => user.enabled).length },
    { label: 'Blocked accounts', value: adminPreviewUsers.filter(user => !user.enabled).length },
    { label: 'Public Decks', value: adminPreviewDecks.length },
  ]
  return <div className="db-page admin-page">
    <header className="admin-heading"><p className="db-eyebrow">{t('ADMINISTRATION')}</p><h1>{t('Admin Panel')}</h1><p>{t('Manage the FlashMind community and public decks.')}</p></header>
    <p className="admin-preview-note" role="note"><strong>{t('Preview')}</strong> · {t('Sample data. Account and deck actions are not available yet.')}</p>
    <div className="admin-metrics">{metrics.map(metric => <div className="admin-metric" key={metric.label}><span>{t(metric.label)}</span><strong>{number.format(metric.value)}</strong></div>)}</div>
    <nav className="admin-sections" aria-label={t('Administration sections')}>
      <button type="button" aria-pressed={section === 'users'} onClick={() => setSection('users')}>{t('Users')}<span>{number.format(adminPreviewUsers.length)}</span></button>
      <button type="button" aria-pressed={section === 'decks'} onClick={() => setSection('decks')}>{t('Public Decks')}<span>{number.format(adminPreviewDecks.length)}</span></button>
    </nav>
    {section === 'users' ? <UsersPreview /> : <DecksPreview />}
  </div>
}

function UsersPreview() {
  const { t, locale } = useLanguage()
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const query = search.trim().toLocaleLowerCase(locale)
  const users = adminPreviewUsers.filter(user => (!role || user.role === role) && (!status || user.enabled === (status === 'active'))
    && `${user.username} ${user.email}`.toLocaleLowerCase(locale).includes(query))
  return <section className="admin-panel" aria-labelledby="admin-users-title">
    <div className="admin-panel-heading"><h2 id="admin-users-title">{t('Users')}</h2><p role="status">{t('Results: {0}', [users.length.toLocaleString(locale)])}</p></div>
    <div className="admin-filters" role="search" aria-label={t('Search users')}>
      <FormField id="admin-user-search" label={t('Search users')} type="search" placeholder={t('Username or email…')} value={search} maxLength={120} onChange={event => { setSearch(event.target.value); setPage(0) }} />
      <SelectField id="admin-user-role" label={t('Role')} value={role} onChange={event => { setRole(event.target.value); setPage(0) }}>
        <option value="">{t('All roles')}</option><option value="USER">USER</option><option value="ADMIN">ADMIN</option>
      </SelectField>
      <SelectField id="admin-user-status" label={t('Account status')} value={status} onChange={event => { setStatus(event.target.value); setPage(0) }}>
        <option value="">{t('All statuses')}</option><option value="active">{t('Active')}</option><option value="blocked">{t('Blocked')}</option>
      </SelectField>
      <button className="button button-outline" type="button" onClick={() => { setSearch(''); setRole(''); setStatus(''); setPage(0) }}>{t('Clear filters')}</button>
    </div>
    {users.length === 0 ? <EmptyResults /> : <>
      <div className="admin-table-scroll" tabIndex={0} role="region" aria-label={t('Users table')}><table className="admin-table">
        <caption className="db-sr-only">{t('Users table')}</caption>
        <thead><tr>{['User', 'Role', 'Account status', 'Email verification', 'Joined', 'Actions'].map(label => <th key={label} scope="col">{t(label)}</th>)}</tr></thead>
        <tbody>{users.slice(page * pageSize, (page + 1) * pageSize).map(user => <tr key={user.id}>
          <th scope="row"><div className="admin-user-cell"><span className="admin-avatar" aria-hidden="true">{user.username.slice(0, 2).toUpperCase()}</span><div><strong>{user.username}</strong><span>{user.email}</span></div></div></th>
          <td><span className={`admin-badge${user.role === 'ADMIN' ? ' is-admin' : ''}`}>{user.role}</span></td>
          <td><span className={`admin-badge ${user.enabled ? 'is-active' : 'is-blocked'}`}>{user.enabled ? t('Active') : t('Blocked')}</span></td>
          <td>{user.verified ? t('Verified') : t('Not verified')}</td>
          <td><time dateTime={user.createdAt}>{date(user.createdAt, locale)}</time></td>
          <td><div className="admin-row-actions">
            <PreviewAction icon={editIcon} label={t('Edit user {0}', [user.username])} />
            <PreviewAction icon={user.enabled ? blockIcon : unblockIcon} label={t(user.enabled ? 'Block {0}' : 'Unblock {0}', [user.username])} />
            <PreviewAction icon={deleteIcon} label={t('Delete user {0}', [user.username])} danger />
          </div></td>
        </tr>)}</tbody>
      </table></div>
      <Pagination page={page} total={users.length} onChange={setPage} label={t('User pagination')} />
    </>}
  </section>
}

function DecksPreview() {
  const { t, locale, count } = useLanguage()
  const [search, setSearch] = useState('')
  const [type, setType] = useState('')
  const [page, setPage] = useState(0)
  const query = search.trim().toLocaleLowerCase(locale)
  const decks = adminPreviewDecks.filter(deck => (!type || deck.deckType === type)
    && `${deck.name} ${deck.author ?? t('Deleted user')}`.toLocaleLowerCase(locale).includes(query))
  return <section className="admin-panel" aria-labelledby="admin-decks-title">
    <div className="admin-panel-heading"><h2 id="admin-decks-title">{t('Public Decks')}</h2><p role="status">{t('Results: {0}', [decks.length.toLocaleString(locale)])}</p></div>
    <div className="admin-filters admin-deck-filters" role="search" aria-label={t('Search public decks')}>
      <FormField id="admin-deck-search" label={t('Search public decks')} type="search" placeholder={t('Deck name or author…')} value={search} maxLength={120} onChange={event => { setSearch(event.target.value); setPage(0) }} />
      <SelectField id="admin-deck-type" label={t('Deck type')} value={type} onChange={event => { setType(event.target.value); setPage(0) }}>
        <option value="">{t('All types')}</option><option value="GENERAL">{t('General')}</option><option value="LANGUAGE">{t('Language')}</option>
      </SelectField>
      <button className="button button-outline" type="button" onClick={() => { setSearch(''); setType(''); setPage(0) }}>{t('Clear filters')}</button>
    </div>
    {decks.length === 0 ? <EmptyResults /> : <>
      <div className="admin-table-scroll" tabIndex={0} role="region" aria-label={t('Public decks table')}><table className="admin-table">
        <caption className="db-sr-only">{t('Public decks table')}</caption>
        <thead><tr>{['Deck', 'Author', 'Deck type', 'Flashcards', 'Followers', 'Created', 'Actions'].map(label => <th key={label} scope="col">{t(label)}</th>)}</tr></thead>
        <tbody>{decks.slice(page * pageSize, (page + 1) * pageSize).map(deck => <tr key={deck.id}>
          <th scope="row">{deck.name}</th><td>{deck.author ?? t('Deleted user')}</td><td><span className="admin-badge">{deck.deckType === 'LANGUAGE' ? t('Language') : t('General')}</span></td>
          <td>{count(deck.cardCount, 'card')}</td><td>{deck.followers.toLocaleString(locale)}</td><td><time dateTime={deck.createdAt}>{date(deck.createdAt, locale)}</time></td>
          <td><div className="admin-row-actions">
            <PreviewAction icon={editIcon} label={t('Edit {0}', [deck.name])} />
            <PreviewAction icon={deleteIcon} label={t('Delete {0}', [deck.name])} danger />
          </div></td>
        </tr>)}</tbody>
      </table></div>
      <Pagination page={page} total={decks.length} onChange={setPage} label={t('Public deck pagination')} />
    </>}
  </section>
}

function PreviewAction({ icon, label, danger = false }: { icon: string; label: string; danger?: boolean }) {
  const { t } = useLanguage()
  return <button className={`admin-action-button${danger ? ' is-danger' : ''}`} type="button" disabled aria-label={label} title={t('{0} — coming soon', [label])}>
    <span className="admin-action-icon" aria-hidden="true" style={{ maskImage: `url("${icon}")` }} />
  </button>
}

function Pagination({ page, total, onChange, label }: { page: number; total: number; onChange: (page: number) => void; label: string }) {
  const { t, locale } = useLanguage()
  const pages = Math.ceil(total / pageSize)
  if (pages <= 1) return null
  return <nav className="admin-pagination" aria-label={label}>
    <button className="button button-outline" type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>{t('← Previous')}</button>
    <span>{t('Page {0} of {1}', [(page + 1).toLocaleString(locale), pages.toLocaleString(locale)])}</span>
    <button className="button button-outline" type="button" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)}>{t('Next →')}</button>
  </nav>
}

function EmptyResults() {
  const { t } = useLanguage()
  return <div className="admin-empty" role="status"><h3>{t('No matching results')}</h3><p>{t('Try another search or clear your filters.')}</p></div>
}

function date(value: string, locale: string) { return new Date(value).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' }) }
