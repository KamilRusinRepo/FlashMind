import { useLanguage } from '../i18n/LanguageContext'
import { categoryName, languageName } from '../i18n/labels'
import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { DeckApiError, getStudyDeck, type OwnedDeck } from '../api/decks'
import { createFlashcard, updateFlashcard } from '../api/flashcards'
import { useAuth } from '../auth/AuthContext'
import DeckFlashcards from '../components/flashcards/DeckFlashcards'
import StudyStartButton from '../components/study/StudyStartButton'
import FlashcardDialog from '../components/flashcards/FlashcardDialog'
import FlashcardForm from '../components/flashcards/FlashcardForm'
import DeleteFlashcardDialog from '../components/flashcards/DeleteFlashcardDialog'
import DeleteDeckDialog from '../components/decks/DeleteDeckDialog'
import type { PreviewFlashcard } from '../data/flashcardPreview'
import './MyDecks.css'
import './DeckDetails.css'

type LoadState = { status: 'loading' } | { status: 'ready'; deck: OwnedDeck } | { status: 'error'; message: string; code: number }

export default function DeckDetailsPage() {
  const { t } = useLanguage()
  const { deckId } = useParams()
  const { user } = useAuth()
  const id = Number(deckId)
  const valid = /^\d+$/.test(deckId ?? '') && Number.isSafeInteger(id) && id > 0

  return <div className="db-page deck-details-page">
    <Link to="/decks" className="back-link">{t("← Back to My Decks")}</Link>
    {valid ? <DeckDetailsLoader key={`${user?.id}:${id}`} deckId={id} /> : <DeckUnavailable />}
  </div>
}

function DeckDetailsLoader({ deckId }: { deckId: number }) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getStudyDeck(deckId, controller.signal).then(deck => {
      if (!controller.signal.aborted) setState({ status: 'ready', deck })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load this deck.', code: cause instanceof DeckApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deckId, refresh, attempt])

  if (state.status === 'loading') return <p className="decks-message" role="status">{t("Loading your deck…")}</p>
  if (state.status === 'error') return state.code === 404 ? <DeckUnavailable /> : <div className="decks-message">
    <p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t("Try again")}</button>
  </div>
  return state.deck.ownDeck ? <DeckDetails deck={state.deck} /> : <Navigate to={`/explore/${state.deck.id}`} replace />
}

function DeckDetails({ deck }: { deck: OwnedDeck }) {
  const { t, locale } = useLanguage()
  const navigate = useNavigate()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const languageDeck = deck.deckType === 'LANGUAGE'
  const [dialog, setDialog] = useState<{ mode: 'create' } | { mode: 'edit' | 'delete'; card: PreviewFlashcard } | null>(null)
  const [flashcardRevision, setFlashcardRevision] = useState(0)
  const [createdRevision, setCreatedRevision] = useState(0)
  const [notice, setNotice] = useState('')
  const addButtonRef = useRef<HTMLButtonElement>(null)
  const tone = languageDeck ? 'purple' : 'blue'


  return <>
    <div className="deck-details-overview">
    <header className="deck-details-hero">
      <div className={`deck-details-cover db-tone-${tone}`} aria-hidden="true">
        {deck.backgroundUrl && failedImage !== deck.backgroundUrl
          ? <img src={deck.backgroundUrl} alt="" referrerPolicy="no-referrer" onError={() => setFailedImage(deck.backgroundUrl)} />
          : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}
      </div>
      <div className="deck-details-heading"><div className="db-tags"><span>{deck.publicDeck ? t("PUBLIC") : t("PRIVATE")}</span><span className={`db-tone-${tone}`}>{deck.category ? categoryName(deck.category, t) : t("Uncategorized")}</span></div>
        <h1>{deck.name}</h1>{deck.description?.trim() && <p>{deck.description}</p>}
      </div>
    </header>
    <div className="deck-details-summary">
    <dl className="deck-details-meta">
      <div><dt>{t("Deck type")}</dt><dd>{languageDeck ? t("Language") : t("General")}</dd></div>
      <div><dt>{languageDeck ? t("Languages") : t("Language")}</dt><dd>{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> → {languageName(deck.targetLanguage, t)}</>}</dd></div>
      <div><dt>{t("Created")}</dt><dd><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'long', day: 'numeric' })}</time></dd></div>
    </dl>
    <div className="deck-details-actions">
      <Link className="button button-outline" to={`/decks/${deck.id}/edit`}>{t("Edit Deck")}</Link>
      <button ref={addButtonRef} className="button button-outline" type="button" onClick={() => setDialog({ mode: 'create' })}>{t("＋ Add Flashcard")}</button>
      <StudyStartButton deckId={deck.id} />
      <button className="button deck-delete-button" type="button" onClick={() => setConfirmingDelete(true)}>{t('Delete Deck')}</button>
    </div>
    </div>
    </div>
    {notice && <p className="form-notice form-success" role="status">{t(notice)}</p>}
    {confirmingDelete && <DeleteDeckDialog deck={deck} onCancel={() => setConfirmingDelete(false)}
      onDeleted={() => navigate('/decks', { replace: true, state: { deletedDeckId: deck.id } })}
      onUnavailable={() => navigate('/decks', { replace: true })} />}
    <DeckFlashcards key={`${deck.id}:${createdRevision}`} deckId={deck.id} revision={flashcardRevision} onAdd={() => setDialog({ mode: 'create' })}
      onEdit={card => setDialog({ mode: 'edit', card })} onDelete={card => setDialog({ mode: 'delete', card })} />
    {dialog && dialog.mode !== 'delete' && <FlashcardDialog title={dialog.mode === 'edit' ? t("Edit Flashcard") : t("Add Flashcard")} onCancel={() => setDialog(null)}>
      <FlashcardForm initialValues={dialog.mode === 'edit' ? dialog.card : undefined} onSubmit={async values => {
        if (dialog.mode === 'edit') {
          await updateFlashcard(deck.id, Number(dialog.card.id), values)
          setNotice('Your flashcard has been updated.')
        } else {
          await createFlashcard(deck.id, values)
          setCreatedRevision(value => value + 1)
          setNotice('Your flashcard has been added.')
        }
        setFlashcardRevision(value => value + 1)
        setDialog(null)
        requestAnimationFrame(() => addButtonRef.current?.focus())
      }} onCancel={() => setDialog(null)} />
    </FlashcardDialog>}
    {dialog?.mode === 'delete' && <DeleteFlashcardDialog deckId={deck.id} card={dialog.card} onCancel={() => setDialog(null)}
      onDeleted={() => { setNotice('Your flashcard has been deleted.'); setFlashcardRevision(value => value + 1); setDialog(null); requestAnimationFrame(() => addButtonRef.current?.focus()) }}
      onUnavailable={() => { setNotice(''); setFlashcardRevision(value => value + 1); setDialog(null); requestAnimationFrame(() => addButtonRef.current?.focus()) }} />}
  </>
}

function DeckUnavailable() {
  const { t } = useLanguage()
  return <section className="decks-empty"><h1>{t("Deck not found")}</h1><p>{t("This deck is unavailable or does not belong to your account.")}</p><Link className="button button-primary" to="/decks">{t("Go to My Decks")}</Link></section>
}
