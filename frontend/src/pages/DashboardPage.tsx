import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import DashboardStats from '../components/dashboard/DashboardStats'
import ExploreDeckCard, { LearningCard } from '../components/dashboard/DeckCard'
import OwnedDeckCard from '../components/decks/OwnedDeckCard'
import { exploreDecks } from '../data/dashboard'
import { DeckApiError, getMyDecks, type DeckPage } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import './MyDecks.css'

type DeckState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; page: DeckPage }

export default function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null
  return <DashboardContent key={user.id} username={user.username} />
}

function DashboardContent({ username }: { username: string }) {
  const { refresh } = useAuth()
  const [state, setState] = useState<DeckState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getMyDecks(0, '', controller.signal).then(page => {
      if (!controller.signal.aborted) setState({ status: 'ready', page })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load your decks. Please try again.' })
    })
    return () => controller.abort()
  }, [attempt, refresh])

  function reload(deleted = false) {
    setNotice(deleted ? 'Your deck has been deleted.' : '')
    setState({ status: 'loading' })
    setAttempt(value => value + 1)
  }

  const decks = state.status === 'ready' ? state.page.content : []
  const total = state.status === 'ready' ? state.page.totalElements : 0

  return <div className="db-page">
    <header className="db-hero">
      <div className="db-welcome"><p className="db-eyebrow">A LITTLE PRACTICE. A LASTING MEMORY.</p>
        <h1>Welcome back, {username}!</h1>
        <p>A little practice today. A little more confidence tomorrow.</p>
      </div>
      <DashboardStats />
    </header>
    {notice && <p className="form-notice form-success db-dashboard-notice" role="status">{notice}</p>}
    <section className="db-section" aria-labelledby="continue-title">
      <div className="db-section-heading"><div><h2 id="continue-title">Continue Learning</h2><p>Your latest decks, ready for your next step.</p></div>
        <Link to="/decks" className="db-text-action">View All Decks <span aria-hidden="true">→</span></Link>
      </div>
      {state.status === 'loading' ? <p className="db-section-placeholder">Loading your decks…</p>
        : state.status === 'error' ? <p className="db-section-placeholder">Your decks are temporarily unavailable.</p>
        : decks.length === 0 ? <p className="db-section-placeholder">Create your first deck to get started.</p>
        : <div className="db-learning-grid">{decks.slice(0, 3).map(deck => <LearningCard key={deck.id} deck={deck} />)}</div>}
    </section>
    <section className="db-section" aria-labelledby="my-decks-title">
      <div className="db-section-heading"><h2 id="my-decks-title">My Decks</h2><div className="db-heading-actions">
        <Link className="db-text-action" to="/decks">View all{state.status === 'ready' ? ` (${total})` : ''}</Link>
        <Link className="db-create-action" to="/decks/create"><span aria-hidden="true">＋</span> Create Deck</Link>
      </div></div>
      {state.status === 'loading' ? <p className="decks-message" role="status">Loading your decks…</p>
        : state.status === 'error' ? <div className="decks-message"><p className="form-notice form-error" role="alert">{state.message}</p>
          <button className="button button-outline" type="button" onClick={() => reload()}>Try again</button></div>
        : decks.length === 0 ? <div className="decks-empty"><h3>No decks yet</h3><p>Create a deck and make a little room for learning.</p>
          <Link className="button button-primary" to="/decks/create">Create your first deck</Link></div>
        : <div className="db-deck-grid">{decks.slice(0, 3).map(deck => <OwnedDeckCard key={deck.id} deck={deck} onChanged={reload} />)}</div>}
    </section>
    <section className="db-section" aria-labelledby="explore-title">
      <div className="db-section-heading"><div><h2 id="explore-title">Explore More</h2><p>Sample public decks. Explore is coming soon.</p></div><button className="db-text-action" type="button" disabled title="Exploring public decks is coming soon">Explore Marketplace <span aria-hidden="true">→</span></button></div>
      <div className="db-deck-grid">{exploreDecks.map(deck => <ExploreDeckCard key={deck.id} deck={deck} />)}</div>
    </section>
    <footer className="db-page-footer">Small steps. Big progress.<span>Your decks. Your pace.</span></footer>
  </div>
}
