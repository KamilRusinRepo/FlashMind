import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { DeckApiError, getMyDecks, type DeckPage } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import OwnedDeckCard from '../components/decks/OwnedDeckCard'
import './MyDecks.css'

export default function MyDecksPage() {
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()
  const { state } = useLocation()
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const successNotice = notice ?? (typeof state?.updatedDeckId === 'number' ? 'Your deck has been updated.'
    : typeof state?.createdDeckId === 'number' ? 'Your deck has been created.' : '')
  const search = (params.get('search') ?? '').trim().slice(0, 120)
  const pageValue = params.get('page') ?? '0'
  const page = /^\d+$/.test(pageValue) && Number(pageValue) <= 1000000 ? Number(pageValue) : 0

  const goToPage = useCallback((nextPage: number) => {
    const next = new URLSearchParams()
    if (search) next.set('search', search)
    if (nextPage > 0) next.set('page', String(nextPage))
    setParams(next)
  }, [search, setParams])

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('search') ?? '').trim()
    setParams(value ? { search: value } : {})
  }

  return <div className="db-page">
    {successNotice && <p className="form-notice form-success" role="status">{successNotice}</p>}
    <header className="db-section-heading decks-heading"><div><p className="db-eyebrow">YOUR PERSONAL LIBRARY</p><h1>My Decks</h1>
      <p>Your ideas, organized for your next learning session.</p></div>
      <Link className="db-create-action" to="/decks/create">＋ Create Deck</Link>
    </header>
    <form key={search} className="decks-search" role="search" onSubmit={handleSearch}>
      <FormField id="deck-search" name="search" label="Search your decks" type="search" defaultValue={search} maxLength={120} placeholder="Search by deck name…" />
      <button className="button button-primary" type="submit">Search</button>
      {search && <button className="button button-outline" type="button" onClick={() => setParams({})}>Clear</button>}
    </form>
    <DeckResults key={JSON.stringify([user?.id, page, search, revision])} page={page} search={search} goToPage={goToPage}
      onChanged={deleted => { setNotice(deleted ? 'Your deck has been deleted.' : ''); setRevision(value => value + 1) }} />
  </div>
}

type ResultsState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'success'; data: DeckPage }

function DeckResults({ page, search, goToPage, onChanged }: { page: number; search: string; goToPage: (page: number) => void; onChanged: (deleted: boolean) => void }) {
  const { refresh } = useAuth()
  const [state, setState] = useState<ResultsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getMyDecks(page, search, controller.signal).then(data => {
      if (controller.signal.aborted) return
      if (data.content.length === 0 && page > 0) { goToPage(Math.max(0, Math.min(page - 1, data.totalPages - 1))); return }
      setState({ status: 'success', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) {
        refresh()
        return
      }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load your decks. Please try again.' })
    })
    return () => controller.abort()
  }, [page, search, attempt, refresh, goToPage])

  if (state.status === 'loading') return <p className="decks-message" role="status">Loading your decks…</p>
  if (state.status === 'error') return <div className="decks-message"><p className="form-notice form-error" role="alert">{state.message}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Try again</button></div>

  const { data } = state
  if (data.content.length === 0) return <section className="decks-empty" aria-labelledby="empty-decks-title">
    <span className="decks-empty-icon" aria-hidden="true">▤</span>
    <h2 id="empty-decks-title">{page > 0 ? 'This page has no decks' : search ? 'No matching decks' : 'No decks yet'}</h2>
    <p>{page > 0 ? 'Return to the first page to see the current results.' : search ? 'Try another name or clear your search.' : 'Your decks will appear here once you create them.'}</p>
    {page > 0 && <button className="button button-outline" type="button" onClick={() => goToPage(0)}>Go to first page</button>}
  </section>

  return <section aria-label="Your decks">
    <p className="decks-result-count" role="status">{data.totalElements} {data.totalElements === 1 ? 'deck' : 'decks'}{search && ` matching “${search}”`}</p>
    <div className="db-deck-grid">{data.content.map(deck => <OwnedDeckCard key={deck.id} deck={deck} showActions onChanged={onChanged} />)}</div>
    <nav className="decks-pagination" aria-label="Deck pagination">
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => goToPage(page - 1)}>← Previous</button>
      <span>Page {page + 1} of {data.totalPages}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>Next →</button>
    </nav>
  </section>
}
