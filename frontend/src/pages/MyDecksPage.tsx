import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { DeckListError, getMyDecks, type DeckPage } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import OwnedDeckCard from '../components/decks/OwnedDeckCard'
import './MyDecks.css'

export default function MyDecksPage() {
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()
  const { state } = useLocation()
  const search = (params.get('search') ?? '').trim().slice(0, 120)
  const pageValue = params.get('page') ?? '0'
  const page = /^\d+$/.test(pageValue) && Number(pageValue) <= 1000000 ? Number(pageValue) : 0

  function goToPage(nextPage: number) {
    const next = new URLSearchParams()
    if (search) next.set('search', search)
    if (nextPage > 0) next.set('page', String(nextPage))
    setParams(next)
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('search') ?? '').trim()
    setParams(value ? { search: value } : {})
  }

  return <div className="db-page">
    {typeof state?.createdDeckId === 'number' && <p className="form-notice form-success" role="status">Your deck has been created.</p>}
    <header className="db-section-heading decks-heading"><div><p className="db-eyebrow">YOUR PERSONAL LIBRARY</p><h1>My Decks</h1>
      <p>Your ideas, organized for your next learning session.</p></div>
      <Link className="db-create-action" to="/decks/create">＋ Create Deck</Link>
    </header>
    <form key={search} className="decks-search" role="search" onSubmit={handleSearch}>
      <FormField id="deck-search" name="search" label="Search your decks" type="search" defaultValue={search} maxLength={120} placeholder="Search by deck name…" />
      <button className="button button-primary" type="submit">Search</button>
      {search && <button className="button button-outline" type="button" onClick={() => setParams({})}>Clear</button>}
    </form>
    <DeckResults key={JSON.stringify([user?.id, page, search])} page={page} search={search} goToPage={goToPage} />
  </div>
}

type ResultsState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'success'; data: DeckPage }

function DeckResults({ page, search, goToPage }: { page: number; search: string; goToPage: (page: number) => void }) {
  const { refresh } = useAuth()
  const [state, setState] = useState<ResultsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getMyDecks(page, search, controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ status: 'success', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckListError && cause.status === 401) {
        refresh()
        return
      }
      setState({ status: 'error', message: cause instanceof DeckListError ? cause.message : 'Unable to load your decks. Please try again.' })
    })
    return () => controller.abort()
  }, [page, search, attempt, refresh])

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
    <div className="db-deck-grid">{data.content.map(deck => <OwnedDeckCard key={deck.id} deck={deck} />)}</div>
    <nav className="decks-pagination" aria-label="Deck pagination">
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => goToPage(page - 1)}>← Previous</button>
      <span>Page {page + 1} of {data.totalPages}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>Next →</button>
    </nav>
  </section>
}
