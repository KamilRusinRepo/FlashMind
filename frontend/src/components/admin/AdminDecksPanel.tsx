import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { AdminDecksApiError, getAdminDecks, type AdminDeckFilters, type AdminDeckPage } from '../../api/adminDecks'
import type { AdminUserSummary } from '../../api/adminUsers'
import type { PublicExploreDeck } from '../../api/explore'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import FormField from '../FormField'
import SelectField from '../SelectField'
import AdminEditDeckDialog from './AdminEditDeckDialog'
import AdminDeleteDeckDialog from './AdminDeleteDeckDialog'
import editIcon from '../../assets/icons/admin/edit.svg'
import deleteIcon from '../../assets/icons/admin/delete.svg'

const emptyFilters: AdminDeckFilters = { search: '', deckType: '' }
type LoadState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: AdminDeckPage }

export default function AdminDecksPanel({ onSummary }: { onSummary: (summary: AdminUserSummary) => void }) {
  const { t } = useLanguage()
  const [draft, setDraft] = useState(emptyFilters)
  const [filters, setFilters] = useState(emptyFilters)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState('')
  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setFilters({ ...draft, search: draft.search.trim() }); setPage(0); setNotice(''); setRevision(value => value + 1)
  }
  return <section className="admin-panel" aria-labelledby="admin-decks-title">
    <div className="admin-panel-heading"><h2 id="admin-decks-title">{t('Public Decks')}</h2></div>
    {notice && <p className="form-notice form-success" role="status">{t(notice)}</p>}
    <form className="admin-filters admin-deck-filters" role="search" aria-label={t('Search public decks')} onSubmit={apply}>
      <FormField id="admin-deck-search" label={t('Search public decks')} type="search" placeholder={t('Deck name or author…')} value={draft.search} maxLength={120}
        onChange={event => setDraft(value => ({ ...value, search: event.target.value }))} />
      <SelectField id="admin-deck-type" label={t('Deck type')} value={draft.deckType} onChange={event => setDraft(value => ({ ...value, deckType: event.target.value }))}>
        <option value="">{t('All types')}</option><option value="GENERAL">{t('General')}</option><option value="LANGUAGE">{t('Language')}</option>
      </SelectField>
      <div className="admin-filter-actions"><button className="button button-primary" type="submit">{t('Search')}</button>
        <button className="button button-outline" type="button" onClick={() => { setDraft(emptyFilters); setFilters(emptyFilters); setPage(0); setNotice(''); setRevision(value => value + 1) }}>{t('Clear filters')}</button></div>
    </form>
    <DeckResults key={JSON.stringify([filters, page, revision])} filters={filters} page={page} onPageChange={setPage} onSummary={onSummary}
      onEdited={() => { setNotice('The public deck has been updated.'); setRevision(value => value + 1) }}
      onChanged={deleted => { setNotice(deleted ? 'The public deck has been deleted.' : ''); setRevision(value => value + 1) }} />
  </section>
}

function DeckResults({ filters, page, onPageChange, onSummary, onChanged, onEdited }: {
  onEdited: () => void
  filters: AdminDeckFilters; page: number; onPageChange: (page: number) => void; onSummary: (summary: AdminUserSummary) => void; onChanged: (deleted: boolean) => void
}) {
  const { t, locale, count } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<{ mode: 'edit' | 'delete'; deck: PublicExploreDeck } | null>(null)
  const { search, deckType } = filters
  useEffect(() => {
    const controller = new AbortController()
    getAdminDecks({ search, deckType }, page, controller.signal).then(data => {
      if (controller.signal.aborted) return
      onSummary(data.summary)
      if (page > 0 && data.content.length === 0) { onPageChange(Math.max(0, data.totalPages - 1)); return }
      setState({ status: 'ready', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof AdminDecksApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setState({ status: 'error', message: cause instanceof AdminDecksApiError ? cause.message : 'Unable to load public decks. Please try again.' })
    })
    return () => controller.abort()
  }, [search, deckType, page, attempt, refresh, onSummary, onPageChange])

  if (state.status === 'loading') return <p className="admin-empty" role="status">{t('Loading public decks…')}</p>
  if (state.status === 'error') return <div className="admin-empty"><p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button></div>
  const { data } = state
  return <>
    <p className="admin-result-count" role="status">{t('Results: {0}', [data.totalElements.toLocaleString(locale)])}</p>
    {data.content.length === 0 ? <div className="admin-empty"><h3>{t('No matching results')}</h3><p>{t('Try another search or clear your filters.')}</p></div>
      : <div className="admin-table-scroll" tabIndex={0} role="region" aria-label={t('Public decks table')}><table className="admin-table">
        <caption className="db-sr-only">{t('Public decks table')}</caption><thead><tr>{['Deck', 'Author', 'Deck type', 'Flashcards', 'Followers', 'Created', 'Actions'].map(label => <th key={label} scope="col">{t(label)}</th>)}</tr></thead>
        <tbody>{data.content.map(deck => <tr key={deck.id}>
          <th scope="row"><Link to={`/explore/${deck.id}`} aria-label={t('Open {0}', [deck.name])}>{deck.name}</Link></th>
          <td>{deck.author.id === null ? t('Deleted user') : deck.author.displayName}</td>
          <td><span className="admin-badge">{deck.deckType === 'LANGUAGE' ? t('Language') : t('General')}</span></td>
          <td>{count(deck.cardCount, 'card')}</td><td>{deck.followerCount.toLocaleString(locale)}</td>
          <td><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })}</time></td>
          <td><div className="admin-row-actions">
            <button className="admin-action-button" type="button" onClick={() => setSelected({ mode: 'edit', deck })} title={t('Edit {0}', [deck.name])} aria-label={t('Edit {0}', [deck.name])} aria-haspopup="dialog">
              <span className="admin-action-icon" aria-hidden="true" style={{ maskImage: `url("${editIcon}")` }} />
            </button>
            <button className="admin-action-button is-danger" type="button" onClick={() => setSelected({ mode: 'delete', deck })} title={t('Delete {0}', [deck.name])} aria-label={t('Delete {0}', [deck.name])}>
              <span className="admin-action-icon" aria-hidden="true" style={{ maskImage: `url("${deleteIcon}")` }} />
            </button></div></td>
        </tr>)}</tbody>
      </table></div>}
    {data.totalPages > 1 && <nav className="admin-pagination" aria-label={t('Public deck pagination')}>
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>{t('← Previous')}</button>
      <span>{t('Page {0} of {1}', [(page + 1).toLocaleString(locale), data.totalPages.toLocaleString(locale)])}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => onPageChange(page + 1)}>{t('Next →')}</button>
    </nav>}
    {selected?.mode === 'delete' && <AdminDeleteDeckDialog deck={selected.deck} onCancel={() => setSelected(null)} onDeleted={() => onChanged(true)} onUnavailable={() => onChanged(false)} />}
    {selected?.mode === 'edit' && <AdminEditDeckDialog deck={selected.deck} onCancel={() => setSelected(null)} onSaved={onEdited} onUnavailable={() => onChanged(false)} />}
  </>
}
