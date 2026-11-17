import { useLanguage } from '../../i18n/LanguageContext'
import { useEffect, useState } from 'react'
import { FlashcardApiError, getFlashcards, type FlashcardPage } from '../../api/flashcards'
import { useAuth } from '../../auth/AuthContext'
import type { PreviewFlashcard } from '../../data/flashcardPreview'
import FlashcardPreview from './FlashcardPreview'

type Props = { deckId: number; revision: number; onAdd: () => void; onEdit: (card: PreviewFlashcard) => void; onDelete: (card: PreviewFlashcard) => void }
type LoadState = { status: 'loading' } | { status: 'ready'; data: FlashcardPage } | { status: 'error'; message: string; code: number }

export default function DeckFlashcards(props: Props) {
  const { t } = useLanguage()
  const [page, setPage] = useState(0)
  return <section className="deck-details-flashcards" aria-labelledby="flashcards-title">
    <div className="db-section-heading"><div><h2 id="flashcards-title">{t("Flashcards")}</h2></div></div>
    <FlashcardResults key={`${props.deckId}:${page}:${props.revision}`} {...props} page={page} onPageChange={setPage} />
  </section>
}

function FlashcardResults({ deckId, page, onPageChange, onAdd, onEdit, onDelete }: Props & { page: number; onPageChange: (page: number) => void }) {
  const { t, count } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getFlashcards(deckId, page, controller.signal).then(data => {
      if (controller.signal.aborted) return
      if (data.content.length === 0 && page > 0) { onPageChange(Math.max(0, data.totalPages - 1)); return }
      setState({ status: 'ready', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof FlashcardApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof FlashcardApiError ? cause.message : 'Unable to load flashcards.',
        code: cause instanceof FlashcardApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deckId, page, attempt, refresh, onPageChange])

  if (state.status === 'loading') return <p className="decks-message" role="status">{t("Loading flashcards…")}</p>
  if (state.status === 'error') return <div className="decks-message">
    <p className="form-notice form-error" role="alert">{t(state.message)}</p>
    {state.code !== 404 && <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t("Try again")}</button>}
  </div>

  const { data } = state
  if (data.totalElements === 0) return <div className="decks-empty">
    <span className="decks-empty-icon" aria-hidden="true">▤</span><h3>{t("No flashcards yet")}</h3>
    <p>{t("This deck has no saved flashcards. Add your first card to get started.")}</p>
    <button className="button button-primary" type="button" onClick={onAdd}>{t("Add your first flashcard")}</button>
  </div>

  return <>
    <p className="decks-result-count" role="status">{count(data.totalElements, 'card')}</p>
    <div className="flashcard-preview-list">{data.content.map((card, index) => {
      const displayedCard: PreviewFlashcard = { id: String(card.id), question: card.question, answer: card.answer,
        exampleSentence: card.exampleSentence ?? undefined, translatedExample: card.translatedExample ?? undefined, imageUrl: card.imageUrl ?? undefined }
      return <FlashcardPreview key={card.id} card={displayedCard} index={page * data.size + index} preview={false}
        onEdit={() => onEdit(displayedCard)} onDelete={() => onDelete(displayedCard)} />
    })}</div>
    <nav className="decks-pagination" aria-label={t("Flashcard pagination")}>
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>{t("← Previous")}</button>
      <span>{t("Page")} {page + 1} {t("of")} {data.totalPages}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => onPageChange(page + 1)}>{t("Next →")}</button>
    </nav>
  </>
}
