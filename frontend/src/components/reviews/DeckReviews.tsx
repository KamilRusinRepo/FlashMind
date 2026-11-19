import { useEffect, useId, useState } from 'react'
import { getDeckReviews, ReviewsApiError, type ReviewPage } from '../../api/reviews'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import RatingStars, { StarIcon } from './RatingStars'
import './DeckReviews.css'

type LoadState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: ReviewPage }

export default function DeckReviews({ deckId, ownDeck, ownerView = false }: { deckId: number; ownDeck: boolean; ownerView?: boolean }) {
  const { t, locale } = useLanguage()
  const { refresh } = useAuth()
  const id = useId()
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  useEffect(() => {
    const controller = new AbortController()
    getDeckReviews(deckId, page, controller.signal, ownerView).then(data => {
      if (controller.signal.aborted) return
      if (page > 0 && data.content.length === 0) { setPage(Math.max(0, data.totalPages - 1)); return }
      setState({ status: 'ready', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof ReviewsApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setState({ status: 'error', message: cause instanceof ReviewsApiError ? cause.message : 'Unable to load reviews. Please try again.' })
    })
    return () => controller.abort()
  }, [deckId, page, attempt, refresh, ownerView])

  const number = new Intl.NumberFormat(locale)
  const summary = state.status === 'ready' ? state.data.summary : null
  function changePage(next: number) { setState({ status: 'loading' }); setPage(next) }
  return <section className="deck-reviews" id="public-deck-reviews" aria-labelledby={`${id}-title`}>
    <div className="db-section-heading"><h2 id={`${id}-title`}>{t('Ratings and reviews')}</h2></div>
    <div className="review-overview">
      <div className="review-summary" aria-label={t('Rating summary')}>
        {state.status === 'loading' && <p role="status">{t('Loading reviews…')}</p>}
        {state.status === 'error' && <>
          <p className="form-notice form-error" role="alert">{t(state.message)}</p>
          <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button>
        </>}
        {summary && <>
          <p className="review-average">{summary.averageRating === null ? <span>{t('No ratings yet')}</span> : <>
            <strong>{new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(summary.averageRating)}</strong><span>{t('out of 5')}</span>
          </>}</p>
          {summary.averageRating !== null && <RatingStars rating={summary.averageRating} />}
          <p className="review-count">{t('{0} reviews', [number.format(summary.reviewCount)])}</p>
          <div className="review-distribution">{summary.distribution.map(bucket => <div key={bucket.rating} aria-label={t('{0} stars: {1} reviews', [number.format(bucket.rating), number.format(bucket.count)])}>
            <span aria-hidden="true">{bucket.rating} ★</span><span className="review-distribution-track" aria-hidden="true"><span style={{ width: `${summary.reviewCount ? bucket.count / summary.reviewCount * 100 : 0}%` }} /></span><span aria-hidden="true">{number.format(bucket.count)}</span>
          </div>)}</div>
        </>}
      </div>
      {ownDeck ? <div className="review-own-note"><h3>{t('Your deck')}</h3><p>{t('You cannot review your own deck.')}</p></div> : <ReviewFormPreview />}
    </div>
    {state.status === 'ready' && <>
      {state.data.totalElements === 0 ? <div className="decks-empty review-list"><h3>{t('No reviews yet')}</h3><p>{t('This deck has not been reviewed yet.')}</p></div>
        : <div className="review-list" aria-label={t('Reviews')}>{state.data.content.map(review => <article className="review-card" key={review.id}>
          <header><div className="review-author"><span className="review-avatar" aria-hidden="true">{review.author.displayName.slice(0, 1).toUpperCase()}</span><strong>{review.author.displayName}</strong>
            {review.ownReview && <span className="review-own-badge">{t('Your review')}</span>}</div>
            <time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })}</time></header>
          <RatingStars rating={review.rating} />
          {review.comment && <p className="review-comment">{review.comment}</p>}
        </article>)}</div>}
      {state.data.totalPages > 1 && <nav className="decks-pagination" aria-label={t('Review pagination')}>
        <button className="button button-outline" type="button" disabled={page === 0} onClick={() => changePage(page - 1)}>{t('← Previous')}</button>
        <span>{t('Page {0} of {1}', [number.format(page + 1), number.format(state.data.totalPages)])}</span>
        <button className="button button-outline" type="button" disabled={page + 1 >= state.data.totalPages} onClick={() => changePage(page + 1)}>{t('Next →')}</button>
      </nav>}
    </>}
  </section>
}

function ReviewFormPreview() {
  const { t, locale } = useLanguage()
  const id = useId()
  const [rating, setRating] = useState(0)
  const [hovered, setHovered] = useState(0)
  const [comment, setComment] = useState('')
  return <form className="review-form" onSubmit={event => event.preventDefault()} aria-describedby={`${id}-hint`}>
    <h3>{t('Write a review')}</h3>
    <fieldset className="review-rating-field"><legend>{t('Your rating')}</legend>
      <div className="review-rating-options" onMouseLeave={() => setHovered(0)}>
        {[1, 2, 3, 4, 5].map(value => <label className="review-rating-choice" key={value} onMouseEnter={() => setHovered(value)}>
          <input type="radio" name={`${id}-rating`} value={value} checked={rating === value} onChange={() => setRating(value)} aria-label={t('Rate {0} out of 5', [value])} />
          <span className={value <= (hovered || rating) ? 'is-selected' : ''}><StarIcon /></span>
        </label>)}
      </div>
      <p className="field-hint" role="status">{rating ? t('Selected rating: {0} out of 5', [rating]) : t('Choose 1 to 5 stars.')}</p>
    </fieldset>
    <div className="review-comment-field"><label htmlFor={`${id}-comment`}>{t('Comment (optional)')}</label>
      <textarea id={`${id}-comment`} name="comment" rows={4} maxLength={1000} value={comment} onChange={event => setComment(event.target.value)} placeholder={t('What did you think of this deck?')} aria-describedby={`${id}-length`} />
      <p className="field-hint review-character-count" id={`${id}-length`}>{t('{0} / {1} characters', [comment.length.toLocaleString(locale), (1000).toLocaleString(locale)])}</p>
    </div>
    <button className="button button-primary" type="submit" disabled aria-describedby={`${id}-hint`}>{t('Submit review')}</button>
    <p className="field-hint" id={`${id}-hint`}>{t('Submitting reviews will be available in a later step.')}</p>
  </form>
}
