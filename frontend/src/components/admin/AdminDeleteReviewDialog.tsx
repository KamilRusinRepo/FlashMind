import { useEffect, useId, useRef, useState } from 'react'
import { deleteAdminReview, type AdminReview } from '../../api/adminReviews'
import { ReviewsApiError } from '../../api/reviews'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'

export default function AdminDeleteReviewDialog({ review, onCancel, onDeleted, onRefresh }: {
  review: AdminReview; onCancel: () => void; onDeleted: () => void; onRefresh: () => void
}) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const id = useId()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const active = useRef<AbortController | null>(null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [needsRefresh, setNeedsRefresh] = useState(false)
  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { active.current?.abort(); if (dialog?.open) dialog.close() }
  }, [])

  async function remove() {
    if (active.current || needsRefresh) return
    const controller = new AbortController()
    active.current = controller; setSending(true); setError('')
    try {
      await deleteAdminReview(review.id, controller.signal)
      if (!controller.signal.aborted) onDeleted()
    } catch (cause) {
      if (controller.signal.aborted) return
      if (cause instanceof ReviewsApiError && (cause.status === 401 || cause.status === 403)) void refresh()
      setError(cause instanceof ReviewsApiError ? cause.message : 'Unable to confirm deletion. Refresh reviews before trying again.')
      setNeedsRefresh(!(cause instanceof ReviewsApiError) || cause.uncertain || [401, 403, 404].includes(cause.status))
    } finally {
      if (!controller.signal.aborted) setSending(false)
      if (active.current === controller) active.current = null
    }
  }

  return <dialog ref={dialogRef} className="admin-status-dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} aria-busy={sending}
    onCancel={event => { event.preventDefault(); if (!active.current && !needsRefresh) onCancel() }}>
    <h2 id={`${id}-title`}>{t('Delete review?')}</h2>
    <p id={`${id}-description`}>{t('Delete the review by {0} for {1}? The rating and comment will be permanently deleted.', [review.author.displayName, review.deck.name])}</p>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    <div className="admin-dialog-actions">
      <button className="button button-outline" type="button" autoFocus disabled={sending || needsRefresh} onClick={() => { if (!active.current) onCancel() }}>{t('Cancel')}</button>
      {needsRefresh ? <button className="button button-primary" type="button" onClick={onRefresh}>{t('Refresh reviews')}</button>
        : <button className="button deck-delete-button" type="button" disabled={sending} onClick={() => void remove()}>{sending ? t('Deleting…') : t('Delete review')}</button>}
    </div>
  </dialog>
}
