import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { createDeckReview, updateDeckReview, ReviewsApiError, type Review, type ReviewFieldErrors } from '../../api/reviews'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import { StarIcon } from './RatingStars'

export default function ReviewForm({ deckId, disabled, onSaved, onRefresh, review, onCancel }: {
  deckId: number; disabled: boolean; onSaved: () => void; onRefresh: () => void; review?: Review; onCancel?: () => void
}) {
  const { t, locale } = useLanguage()
  const { refresh } = useAuth()
  const id = useId()
  const [rating, setRating] = useState(review?.rating ?? 0)
  const [hovered, setHovered] = useState(0)
  const [comment, setComment] = useState(review?.comment ?? '')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<ReviewFieldErrors>({})
  const [needsRefresh, setNeedsRefresh] = useState(false)
  const active = useRef<AbortController | null>(null)
  const firstStar = useRef<HTMLInputElement>(null)
  const commentRef = useRef<HTMLTextAreaElement>(null)
  useEffect(() => () => active.current?.abort(), [])
  const locked = disabled || sending || needsRefresh

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (locked || active.current) return
    const next: ReviewFieldErrors = {}
    if (!Number.isInteger(rating) || rating < 1 || rating > 5) next.rating = 'Choose a whole-star rating from 1 to 5.'
    if (comment.trim().length > 1000) next.comment = 'Use at most 1000 characters.'
    setFields(next); setError('')
    if (Object.keys(next).length) { if (next.rating) firstStar.current?.focus(); else commentRef.current?.focus(); return }
    const controller = new AbortController()
    active.current = controller; setSending(true)
    try {
      if (review) await updateDeckReview(deckId, review.id, rating, comment, controller.signal)
      else await createDeckReview(deckId, rating, comment, controller.signal)
      if (!controller.signal.aborted) onSaved()
    } catch (cause) {
      if (controller.signal.aborted) return
      if (cause instanceof ReviewsApiError && (cause.status === 401 || cause.status === 403)) void refresh()
      setError(cause instanceof ReviewsApiError ? cause.message : review ? 'Unable to update your review. Please try again.' : 'Unable to add your review. Please try again.')
      if (cause instanceof ReviewsApiError) {
        setFields(cause.fieldErrors)
        setNeedsRefresh(cause.uncertain || [401, 403, 404, 409].includes(cause.status))
        requestAnimationFrame(() => { if (cause.fieldErrors.rating) firstStar.current?.focus(); else if (cause.fieldErrors.comment) commentRef.current?.focus() })
      } else { setNeedsRefresh(true); setError('Unable to confirm your review. Refresh reviews before trying again.') }
    } finally {
      if (!controller.signal.aborted) setSending(false)
      if (active.current === controller) active.current = null
    }
  }

  return <form className="review-form" noValidate aria-busy={sending} onSubmit={event => void submit(event)}>
    <h3>{review ? t('Edit review') : t('Write a review')}</h3>
    <fieldset className="review-rating-field" disabled={locked} aria-invalid={!!fields.rating} aria-describedby={`${id}-rating-hint${fields.rating ? ` ${id}-rating-error` : ''}`}><legend>{t('Your rating')}</legend>
      <div className="review-rating-options" onMouseLeave={() => setHovered(0)}>
        {[1, 2, 3, 4, 5].map(value => <label className="review-rating-choice" key={value} onMouseEnter={() => { if (!locked) setHovered(value) }}>
          <input ref={value === 1 ? firstStar : undefined} type="radio" name={`${id}-rating`} value={value} checked={rating === value} required
            onChange={() => setRating(value)} aria-label={t('Rate {0} out of 5', [value])} />
          <span className={value <= (hovered || rating) ? 'is-selected' : ''}><StarIcon /></span>
        </label>)}
      </div>
      <p className="field-hint" id={`${id}-rating-hint`} role="status">{rating ? t('Selected rating: {0} out of 5', [rating]) : t('Choose 1 to 5 stars.')}</p>
      {fields.rating && <p className="field-error" id={`${id}-rating-error`}>{t(fields.rating)}</p>}
    </fieldset>
    <div className="review-comment-field"><label htmlFor={`${id}-comment`}>{t('Comment (optional)')}</label>
      <textarea ref={commentRef} id={`${id}-comment`} name="comment" rows={4} maxLength={1000} value={comment} disabled={locked}
        onChange={event => setComment(event.target.value)} placeholder={t('What did you think of this deck?')}
        aria-invalid={!!fields.comment} aria-describedby={`${id}-length${fields.comment ? ` ${id}-comment-error` : ''}`} />
      <p className="field-hint review-character-count" id={`${id}-length`}>{t('{0} / {1} characters', [comment.length.toLocaleString(locale), (1000).toLocaleString(locale)])}</p>
      {fields.comment && <p className="field-error" id={`${id}-comment-error`}>{t(fields.comment)}</p>}
    </div>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
    {needsRefresh ? <button className="button button-outline" type="button" disabled={disabled} onClick={onRefresh}>{t('Refresh reviews')}</button>
      : <button className="button button-primary" type="submit" disabled={locked}>{sending ? (review ? t('Saving changes…') : t('Submitting review…')) : (review ? t('Save changes') : t('Submit review'))}</button>}
    {review && <button className="button button-outline" type="button" disabled={sending || needsRefresh} onClick={onCancel}>{t('Cancel')}</button>}
  </form>
}
