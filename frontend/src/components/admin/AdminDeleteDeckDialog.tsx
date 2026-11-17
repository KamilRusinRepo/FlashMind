import { useEffect, useId, useRef, useState } from 'react'
import { AdminDecksApiError, deleteAdminDeck } from '../../api/adminDecks'
import type { PublicExploreDeck } from '../../api/explore'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'

export default function AdminDeleteDeckDialog({ deck, onCancel, onDeleted, onUnavailable }: {
  deck: PublicExploreDeck; onCancel: () => void; onDeleted: () => void; onUnavailable: () => void
}) {
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

  async function remove() {
    if (submitting.current || unavailable) return
    submitting.current = true; setSending(true); setError('')
    try {
      await deleteAdminDeck(deck.id)
      if (mounted.current) onDeleted()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof AdminDecksApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setError(cause instanceof AdminDecksApiError ? cause.message : 'Unable to delete this public deck. Please try again.')
      setUnavailable(cause instanceof AdminDecksApiError && cause.status === 404)
    } finally { submitting.current = false; if (mounted.current) setSending(false) }
  }
  return <dialog ref={dialogRef} className="admin-status-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!submitting.current) onCancel() }}>
    <h2 id={titleId}>{t('Delete public deck?')}</h2>
    <p id={descriptionId}>{t('Delete public deck {0}? Its flashcards, follows and study data will be removed for all learners. This action is permanent.', [deck.name])}</p>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <div className="admin-dialog-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!submitting.current) onCancel() }}>{t('Cancel')}</button>
      {unavailable ? <button className="button button-primary" type="button" onClick={onUnavailable}>{t('Refresh public decks')}</button>
        : <button className="button deck-delete-button" type="button" disabled={sending} onClick={() => void remove()}>{sending ? t('Deleting…') : t('Delete Deck')}</button>}
    </div>
  </dialog>
}
