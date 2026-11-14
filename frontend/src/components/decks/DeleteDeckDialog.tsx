import { useLanguage } from '../../i18n/LanguageContext'
import { useEffect, useId, useRef, useState } from 'react'
import { DeckApiError, deleteDeck, type OwnedDeck } from '../../api/decks'
import { useAuth } from '../../auth/AuthContext'

type Props = { deck: OwnedDeck; onCancel: () => void; onDeleted: () => void; onUnavailable: () => void }

export default function DeleteDeckDialog({ deck, onCancel, onDeleted, onUnavailable }: Props) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const submitting = useRef(false)
  const mounted = useRef(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [unavailable, setUnavailable] = useState(false)

  useEffect(() => {
    mounted.current = true
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { mounted.current = false; if (dialog?.open) dialog.close() }
  }, [])

  async function handleDelete() {
    if (submitting.current || unavailable) return
    submitting.current = true
    setSending(true)
    setError('')
    try {
      await deleteDeck(deck.id)
      if (mounted.current) onDeleted()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setError(cause instanceof DeckApiError ? cause.message : 'Unable to delete your deck. Please try again.')
      setUnavailable(cause instanceof DeckApiError && cause.status === 404)
    } finally {
      submitting.current = false
      if (mounted.current) setSending(false)
    }
  }

  return <dialog ref={dialogRef} className="deck-delete-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!submitting.current) onCancel() }}>
    <h2 id={titleId}>{t("Delete deck?")}</h2>
    <p id={descriptionId}>{t("You’re about to delete")} <strong>{deck.name}</strong>{t(". This action is permanent.")}</p>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <div className="deck-form-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!submitting.current) onCancel() }}>{t("Cancel")}</button>
      {unavailable ? <button className="button button-primary" type="button" onClick={onUnavailable}>{t("Refresh My Decks")}</button>
        : <button className="button deck-delete-button" type="button" disabled={sending} onClick={() => void handleDelete()}>{sending ? t("Deleting…") : t("Delete Deck")}</button>}
    </div>
  </dialog>
}
