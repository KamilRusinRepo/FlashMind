import { useEffect, useId, useRef, useState } from 'react'
import { getAdminDeck, updateAdminDeck } from '../../api/adminDecks'
import { DeckApiError, type OwnedDeck } from '../../api/decks'
import type { PublicExploreDeck } from '../../api/explore'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import DeckForm from '../decks/DeckForm'

type LoadState = { status: 'loading' } | { status: 'ready'; deck: OwnedDeck } | { status: 'error'; message: string; code: number }

export default function AdminEditDeckDialog({ deck, onCancel, onSaved, onUnavailable }: {
  deck: PublicExploreDeck; onCancel: () => void; onSaved: () => void; onUnavailable: () => void
}) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const submitting = useRef(false)
  const id = useId()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [sending, setSending] = useState(false)
  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { if (dialog?.open) dialog.close() }
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    getAdminDeck(deck.id, controller.signal).then(value => {
      if (!controller.signal.aborted) setState({ status: 'ready', deck: value })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load this public deck. Please try again.', code: cause instanceof DeckApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deck.id, attempt, refresh])

  return <dialog ref={dialogRef} className="admin-status-dialog admin-edit-deck-dialog" aria-labelledby={`${id}-title`} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!submitting.current) onCancel() }}>
    <h2 id={`${id}-title`}>{t('Edit {0}', [deck.name])}</h2>
    {state.status === 'loading' && <p role="status">{t('Loading public deck…')}</p>}
    {state.status === 'error' && <>
      <p className="form-notice form-error" role="alert">{t(state.message)}</p>
      <div className="admin-dialog-actions"><button className="button button-outline" type="button" onClick={onCancel}>{t('Cancel')}</button>
        {state.code === 404 ? <button className="button button-primary" type="button" onClick={onUnavailable}>{t('Refresh public decks')}</button>
          : <button className="button button-primary" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button>}</div>
    </>}
    {state.status === 'loading' && <button className="button button-outline" type="button" onClick={onCancel}>{t('Cancel')}</button>}
    {state.status === 'ready' && <DeckForm initialValues={state.deck} publicOnly onCancel={onCancel}
      onSendingChange={value => { submitting.current = value; setSending(value) }}
      submitLabel="Save changes" pendingLabel="Saving changes…" onSaved={onSaved}
      save={async request => {
        try { return await updateAdminDeck(deck.id, request) }
        catch (cause) {
          if (cause instanceof DeckApiError && cause.status === 404) {
            submitting.current = false; setSending(false)
            setState({ status: 'error', message: cause.message, code: 404 })
          }
          throw cause
        }
      }} />}
  </dialog>
}
