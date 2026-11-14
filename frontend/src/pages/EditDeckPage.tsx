import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { DeckApiError, getOwnedDeck, updateDeck, type OwnedDeck } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import DeckForm from '../components/decks/DeckForm'

type LoadState = { status: 'loading' } | { status: 'ready'; deck: OwnedDeck } | { status: 'error'; message: string; code: number }

export default function EditDeckPage() {
  const { deckId } = useParams()
  const { user } = useAuth()
  const id = Number(deckId)
  const valid = /^\d+$/.test(deckId ?? '') && Number.isSafeInteger(id) && id > 0
  return <div className="db-page">
    <Link to="/decks" className="back-link">← Back to My Decks</Link>
    <header className="db-section-heading decks-heading"><div><h1>Edit Deck</h1><p>Update your deck’s details and learning languages.</p></div></header>
    {valid ? <DeckEditor key={`${user?.id}:${id}`} deckId={id} /> : <DeckUnavailable />}
  </div>
}

function DeckEditor({ deckId }: { deckId: number }) {
  const { refresh } = useAuth()
  const navigate = useNavigate()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getOwnedDeck(deckId, controller.signal).then(deck => {
      if (!controller.signal.aborted) setState({ status: 'ready', deck })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load this deck.', code: cause instanceof DeckApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deckId, refresh, attempt])

  if (state.status === 'loading') return <p className="decks-message" role="status">Loading your deck…</p>
  if (state.status === 'error') return state.code === 404 ? <DeckUnavailable /> : <div className="decks-message">
    <p className="form-notice form-error" role="alert">{state.message}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Try again</button>
  </div>
  return <DeckForm key={state.deck.id} initialValues={state.deck} cancelTo={`/decks/${deckId}`} save={request => updateDeck(deckId, request)}
    submitLabel="Save changes" pendingLabel="Saving changes…" onSaved={deck => navigate('/decks', { replace: true, state: { updatedDeckId: deck.id } })} />
}

function DeckUnavailable() {
  return <section className="decks-empty"><h2>Deck not found</h2><p>This deck is unavailable or does not belong to your account.</p>
    <Link className="button button-primary" to="/decks">Go to My Decks</Link></section>
}
