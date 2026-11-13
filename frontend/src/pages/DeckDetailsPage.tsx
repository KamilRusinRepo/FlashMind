import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { DeckApiError, getOwnedDeck, type OwnedDeck } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import FlashcardPreview from '../components/flashcards/FlashcardPreview'
import FlashcardDialog from '../components/flashcards/FlashcardDialog'
import FlashcardForm, { type FlashcardValues } from '../components/flashcards/FlashcardForm'
import DeleteFlashcardDialog from '../components/flashcards/DeleteFlashcardDialog'
import { generalFlashcardExamples, languageFlashcardExamples, type PreviewFlashcard } from '../data/flashcardPreview'
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
  const [cards, setCards] = useState<PreviewFlashcard[]>(() => languageDeck ? languageFlashcardExamples : generalFlashcardExamples)
  const [dialog, setDialog] = useState<{ mode: 'create' } | { mode: 'edit' | 'delete'; card: PreviewFlashcard } | null>(null)
  const [notice, setNotice] = useState('')
  const addButtonRef = useRef<HTMLButtonElement>(null)
  const tone = languageDeck ? 'purple' : 'blue'

  useEffect(() => { document.title = `${deck.name} | FlashMind` }, [deck.name])

  function savePreview(values: FlashcardValues) {
    if (!dialog || dialog.mode === 'delete') return
    if (dialog.mode === 'edit') {
      setCards(current => current.map(card => card.id === dialog.card.id ? { ...values, id: card.id } : card))
      setNotice('Flashcard updated in this preview only.')
    } else {
      const card = { ...values, id: crypto.randomUUID() }
      setCards(current => [...current, card])
      setNotice('Flashcard added to this preview only.')
    }
    setShowExamples(true)
    setDialog(null)
  }

  return <>
    <div className="deck-details-overview">
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
    <div className="deck-details-summary">
    <dl className="deck-details-meta">
      <div><dt>Deck type</dt><dd>{languageDeck ? 'Language' : 'General'}</dd></div>
      <div><dt>{languageDeck ? 'Languages' : 'Language'}</dt><dd>{deck.sourceLanguage}{deck.targetLanguage && <> → {deck.targetLanguage}</>}</dd></div>
      <div><dt>Created</dt><dd><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}</time></dd></div>
    </dl>
    <div className="deck-details-actions">
      <Link className="button button-outline" to={`/decks/${deck.id}/edit`}>Edit Deck</Link>
      <button ref={addButtonRef} className="button button-outline" type="button" onClick={() => setDialog({ mode: 'create' })}>＋ Add Flashcard</button>
      <button className="button button-primary" type="button" disabled title="Study sessions are coming soon">Start Study</button>
    </div>
    </div>
    </div>
    <section className="deck-details-flashcards" aria-labelledby="flashcards-title">
      <div className="db-section-heading"><div><h2 id="flashcards-title">Flashcards</h2><p>{showExamples ? `${cards.length} preview cards` : 'Empty list preview'}</p></div>
        <button className="button button-outline" type="button" aria-controls="flashcard-preview-content" aria-pressed={showExamples}
          onClick={() => setShowExamples(value => !value)}>{showExamples ? 'Hide preview cards' : 'Show preview cards'}</button>
      </div>
      <p className="form-notice deck-details-preview-note"><strong>PREVIEW</strong> Flashcard changes are not saved to your deck. They disappear when you leave or reload this page.{languageDeck && ' The initial language examples use English → Polish.'}</p>
      <p className="flashcard-local-status" role="status">{notice}</p>
      <div id="flashcard-preview-content">
        {showExamples && cards.length > 0 ? <div className="flashcard-preview-list">{cards.map((card, index) => <FlashcardPreview key={card.id} card={card} index={index}
          onEdit={() => setDialog({ mode: 'edit', card })} onDelete={() => setDialog({ mode: 'delete', card })} />)}</div>
          : <div className="decks-empty"><span className="decks-empty-icon" aria-hidden="true">▤</span><h3>No flashcards yet</h3><p>Cards you add to this deck will appear here.</p>
            <button className="button button-primary" type="button" onClick={() => setDialog({ mode: 'create' })}>Add your first flashcard</button></div>}
      </div>
    </section>
    {dialog && dialog.mode !== 'delete' && <FlashcardDialog title={dialog.mode === 'edit' ? 'Edit Flashcard' : 'Add Flashcard'} onCancel={() => setDialog(null)}>
      <FlashcardForm initialValues={dialog.mode === 'edit' ? dialog.card : undefined} onSubmit={savePreview} onCancel={() => setDialog(null)} />
    </FlashcardDialog>}
    {dialog?.mode === 'delete' && <DeleteFlashcardDialog card={dialog.card} onCancel={() => setDialog(null)} onDelete={() => {
      setCards(current => current.filter(card => card.id !== dialog.card.id))
      setNotice('Flashcard removed from this preview only.')
      setDialog(null)
      requestAnimationFrame(() => addButtonRef.current?.focus())
    }} />}
  </>
}

function DeckUnavailable() {
  return <section className="decks-empty"><h1>Deck not found</h1><p>This deck is unavailable or does not belong to your account.</p><Link className="button button-primary" to="/decks">Go to My Decks</Link></section>
}
