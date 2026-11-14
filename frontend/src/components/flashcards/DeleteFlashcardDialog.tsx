import { useLanguage } from '../../i18n/LanguageContext'
import { useEffect, useRef, useState } from 'react'
import { deleteFlashcard, FlashcardApiError } from '../../api/flashcards'
import { useAuth } from '../../auth/AuthContext'
import type { PreviewFlashcard } from '../../data/flashcardPreview'
import FlashcardDialog from './FlashcardDialog'

export default function DeleteFlashcardDialog({ deckId, card, onCancel, onDeleted, onUnavailable }: {
  deckId: number; card: PreviewFlashcard; onCancel: () => void; onDeleted: () => void; onUnavailable: () => void
}) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [unavailable, setUnavailable] = useState(false)
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function handleDelete() {
    if (submitting.current || unavailable) return
    submitting.current = true
    setSending(true)
    setError('')
    try {
      await deleteFlashcard(deckId, Number(card.id))
      if (mounted.current) onDeleted()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof FlashcardApiError && cause.status === 401) { refresh(); return }
      setError(cause instanceof FlashcardApiError ? cause.message : 'Unable to delete the flashcard. Please try again.')
      setUnavailable(cause instanceof FlashcardApiError && cause.status === 404)
    } finally {
      submitting.current = false
      if (mounted.current) setSending(false)
    }
  }

  const cancel = () => { if (!submitting.current) onCancel() }
  return <FlashcardDialog title={t("Delete flashcard?")} onCancel={cancel}>
    <p className="flashcard-delete-question">{t("Delete")} <strong>{card.question}</strong>{t("? This action is permanent.")}</p>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <div className="flashcard-dialog-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={cancel}>{t("Cancel")}</button>
      {unavailable ? <button className="button button-primary" type="button" onClick={onUnavailable}>{t("Refresh flashcards")}</button>
        : <button className="button deck-delete-button" type="button" disabled={sending} onClick={() => void handleDelete()}>{sending ? t("Deleting…") : t("Delete Flashcard")}</button>}
    </div>
  </FlashcardDialog>
}
