import { useState } from 'react'
import FormField from '../components/FormField'
import SelectField from '../components/SelectField'
import { adminPreviewDecks } from '../data/adminPreview'
import type { AdminUserSummary } from '../api/adminUsers'
import AdminUsersPanel from '../components/admin/AdminUsersPanel'
import AdminPreviewAction from '../components/admin/AdminPreviewAction'
import { useLanguage } from '../i18n/LanguageContext'
import editIcon from '../assets/icons/admin/edit.svg'
import deleteIcon from '../assets/icons/admin/delete.svg'
import './AdminPage.css'

const pageSize = 5

export default function AdminPage() {
  const { t, locale } = useLanguage()
  const [section, setSection] = useState<'users' | 'decks'>('users')
  const [summary, setSummary] = useState<AdminUserSummary | null>(null)
  const number = new Intl.NumberFormat(locale)
  const metrics = [
    { label: 'Users', value: summary?.users },
    { label: 'Active accounts', value: summary?.activeUsers },
    { label: 'Blocked accounts', value: summary?.blockedUsers },
    { label: 'Public Decks', value: summary?.publicDecks },
  ]
  return <div className="db-page admin-page">
    <header className="admin-heading"><p className="db-eyebrow">{t('ADMINISTRATION')}</p><h1>{t('Admin Panel')}</h1><p>{t('Manage the FlashMind community and public decks.')}</p></header>
    <p className="admin-preview-note" role="note">{section === 'decks' ? <><strong>{t('Preview')}</strong> · {t('Public deck data is still a preview.')}</> : t('User editing and deletion are coming soon.')}</p>
    <div className="admin-metrics">{metrics.map(metric => <div className="admin-metric" key={metric.label}><span>{t(metric.label)}</span><strong>{metric.value === undefined ? '—' : number.format(metric.value)}</strong></div>)}</div>
    <nav className="admin-sections" aria-label={t('Administration sections')}>
      <button type="button" aria-pressed={section === 'users'} onClick={() => setSection('users')}>{t('Users')}<span>{summary ? number.format(summary.users) : '—'}</span></button>
      <button type="button" aria-pressed={section === 'decks'} onClick={() => setSection('decks')}>{t('Public Decks')}<span>{t('Preview')}</span></button>
    </nav>
    {section === 'users' ? <AdminUsersPanel onSummary={setSummary} /> : <DecksPreview />}
  </div>
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
            <AdminPreviewAction icon={editIcon} label={t('Edit {0}', [deck.name])} />
            <AdminPreviewAction icon={deleteIcon} label={t('Delete {0}', [deck.name])} danger />
          </div></td>
        </tr>)}</tbody>
      </table></div>
      <Pagination page={page} total={decks.length} onChange={setPage} label={t('Public deck pagination')} />
    </>}
  </section>
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
