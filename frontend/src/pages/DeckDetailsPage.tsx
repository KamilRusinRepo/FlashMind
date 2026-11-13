import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { DeckApiError, getOwnedDeck, type OwnedDeck } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import FlashcardPreview from '../components/flashcards/FlashcardPreview'
import { generalFlashcardExamples, languageFlashcardExamples } from '../data/flashcardPreview'
import './MyDecks.css'
import './DeckDetails.css'

type LoadState = { status: 'loading' } | { status: 'ready'; deck: OwnedDeck } | { status: 'error'; message: string; code: number }

export default function DeckDetailsPage() {
  const { deckId } = useParams()
  const { user } = useAuth()
  const id = Number(deckId)
  const valid = /^\d+$/.test(deckId ?? '') && Number.isSafeInteger(id) && id > 0

  return <div className="db-page deck-details-page">
    <Link to="/decks" className="back-link">← Back to My Decks</Link>
    {valid ? <DeckDetailsLoader key={`${user?.id}:${id}`} deckId={id} /> : <DeckUnavailable />}
  </div>
}

function DeckDetailsLoader({ deckId }: { deckId: number }) {
  const { refresh } = useAuth()
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
  return <DeckDetails deck={state.deck} />
}

function DeckDetails({ deck }: { deck: OwnedDeck }) {
  const [showExamples, setShowExamples] = useState(true)
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const languageDeck = deck.deckType === 'LANGUAGE'
  const examples = languageDeck ? languageFlashcardExamples : generalFlashcardExamples
  const tone = languageDeck ? 'purple' : 'blue'

  useEffect(() => { document.title = `${deck.name} | FlashMind` }, [deck.name])

  return <>
    <header className="deck-details-hero">
      <div className={`deck-details-cover db-tone-${tone}`} aria-hidden="true">
        {deck.backgroundUrl && failedImage !== deck.backgroundUrl
          ? <img src={deck.backgroundUrl} alt="" referrerPolicy="no-referrer" onError={() => setFailedImage(deck.backgroundUrl)} />
          : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}
      </div>
      <div className="deck-details-heading"><div className="db-tags"><span>{deck.publicDeck ? 'PUBLIC' : 'PRIVATE'}</span><span className={`db-tone-${tone}`}>{deck.category || 'Uncategorized'}</span></div>
        <h1>{deck.name}</h1><p>{deck.description || 'No description yet.'}</p>
      </div>
    </header>
    <dl className="deck-details-meta">
      <div><dt>Deck type</dt><dd>{languageDeck ? 'Language' : 'General'}</dd></div>
      <div><dt>{languageDeck ? 'Languages' : 'Language'}</dt><dd>{deck.sourceLanguage}{deck.targetLanguage && <> → {deck.targetLanguage}</>}</dd></div>
      <div><dt>Created</dt><dd><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}</time></dd></div>
    </dl>
    <div className="deck-details-actions">
      <Link className="button button-outline" to={`/decks/${deck.id}/edit`}>Edit Deck</Link>
      <button className="button button-outline" type="button" disabled title="Adding flashcards is coming soon">＋ Add Flashcard</button>
      <button className="button button-primary" type="button" disabled title="Study sessions are coming soon">Start Study</button>
    </div>
    <section className="deck-details-flashcards" aria-labelledby="flashcards-title">
      <div className="db-section-heading"><div><h2 id="flashcards-title">Flashcards</h2><p>{showExamples ? `${examples.length} example cards` : 'Empty list preview'}</p></div>
        <button className="button button-outline" type="button" aria-controls="flashcard-preview-content" aria-pressed={showExamples}
          onClick={() => setShowExamples(value => !value)}>{showExamples ? 'Hide examples' : 'Show examples'}</button>
      </div>
      <p className="form-notice deck-details-preview-note"><strong>PREVIEW</strong> These cards are examples, not saved flashcards in this deck.{languageDeck && ' The language examples use English → Polish.'}</p>
      <div id="flashcard-preview-content">
        {showExamples ? <div className="flashcard-preview-list">{examples.map((card, index) => <FlashcardPreview key={card.id} card={card} index={index} />)}</div>
          : <div className="decks-empty"><span className="decks-empty-icon" aria-hidden="true">▤</span><h3>No flashcards yet</h3><p>Cards you add to this deck will appear here.</p>
            <button className="button button-primary" type="button" disabled title="Adding flashcards is coming soon">Add your first flashcard</button></div>}
      </div>
    </section>
  </>
}

function DeckUnavailable() {
  return <section className="decks-empty"><h1>Deck not found</h1><p>This deck is unavailable or does not belong to your account.</p><Link className="button button-primary" to="/decks">Go to My Decks</Link></section>
}
