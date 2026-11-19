import { useState } from 'react'
import type { Review } from '../../api/reviews'
import { useLanguage } from '../../i18n/LanguageContext'
import RatingStars from './RatingStars'
import ReviewForm from './ReviewForm'
import DeleteReviewDialog from './DeleteReviewDialog'

export default function OwnReview({ deckId, review, onChanged, onRefresh }: {
  deckId: number; review: Review; onChanged: (notice: string) => void; onRefresh: () => void
}) {
  const { t } = useLanguage()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  if (editing) return <ReviewForm deckId={deckId} review={review} disabled={false} onCancel={() => setEditing(false)}
    onSaved={() => onChanged('Your review has been updated.')} onRefresh={onRefresh} />
  return <div className="review-own-note">
    <h3>{t('Your review')}</h3><RatingStars rating={review.rating} />
    {review.comment && <p className="review-comment">{review.comment}</p>}
    <div className="review-own-actions">
      <button className="button button-outline" type="button" onClick={() => setEditing(true)}>{t('Edit review')}</button>
      <button className="button deck-delete-button" type="button" onClick={() => setDeleting(true)}>{t('Delete review')}</button>
    </div>
    {deleting && <DeleteReviewDialog deckId={deckId} reviewId={review.id} onCancel={() => setDeleting(false)}
      onDeleted={() => onChanged('Your review has been deleted.')} onRefresh={onRefresh} />}
  </div>
}
