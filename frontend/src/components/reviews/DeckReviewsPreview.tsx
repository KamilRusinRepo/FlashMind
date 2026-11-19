import { useId, useState } from 'react'
import { useLanguage } from '../../i18n/LanguageContext'
import RatingStars, { StarIcon } from './RatingStars'
import './DeckReviewsPreview.css'

// Illustrative data only. Replaced by the public deck reviews API in the next stages.
const exampleReviews = [
  { id: 'sample-1', author: 'Alex', rating: 5, comment: 'Clear questions and useful examples. A great deck for a short daily practice.', createdAt: '2026-11-16T10:00:00Z' },
  { id: 'sample-2', author: 'Maya', rating: 4, comment: 'Easy to work through. I would love to see a few more example sentences.', createdAt: '2026-11-15T14:30:00Z' },
  { id: 'sample-3', author: 'Sam', rating: 4, comment: '', createdAt: '2026-11-14T09:15:00Z' },
]
const average = exampleReviews.reduce((sum, review) => sum + review.rating, 0) / exampleReviews.length

export default function DeckReviewsPreview({ ownDeck }: { ownDeck: boolean }) {
  const { t, locale } = useLanguage()
  const id = useId()
  const number = new Intl.NumberFormat(locale)
  return <section className="deck-reviews" id="public-deck-reviews" aria-labelledby={`${id}-title`}>
    <div className="db-section-heading"><h2 id={`${id}-title`}>{t('Ratings and reviews')}</h2><span className="review-preview-badge">{t('PREVIEW')}</span></div>
    <p className="review-preview-note" role="note">{t('These ratings and reviews are examples. Your rating and comment will not be sent or saved yet.')}</p>
    <div className="review-overview">
      <div className="review-summary" aria-label={t('Example rating summary')}>
        <p className="review-average"><strong>{new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(average)}</strong><span>{t('out of 5')}</span></p>
        <RatingStars rating={average} />
        <p className="review-count">{t('{0} example reviews', [number.format(exampleReviews.length)])}</p>
        <div className="review-distribution">{[5, 4, 3, 2, 1].map(rating => {
          const total = exampleReviews.filter(review => review.rating === rating).length
          return <div key={rating} aria-label={t('{0} stars: {1} example reviews', [number.format(rating), number.format(total)])}>
            <span aria-hidden="true">{rating} ★</span><span className="review-distribution-track" aria-hidden="true"><span style={{ width: `${total / exampleReviews.length * 100}%` }} /></span><span aria-hidden="true">{number.format(total)}</span>
          </div>
        })}</div>
      </div>
      {ownDeck ? <div className="review-own-note"><h3>{t('Your deck')}</h3><p>{t('You cannot review your own deck.')}</p></div> : <ReviewFormPreview />}
    </div>
    <div className="review-list" aria-label={t('Example reviews')}>{exampleReviews.map(review => <article className="review-card" key={review.id}>
      <header><div className="review-author"><span className="review-avatar" aria-hidden="true">{review.author.slice(0, 1)}</span><strong>{review.author}</strong></div>
        <time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })}</time></header>
      <RatingStars rating={review.rating} />
      {review.comment && <p className="review-comment">{review.comment}</p>}
    </article>)}</div>
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
