import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getAdminReviews, type AdminReview, type AdminReviewPage } from '../../api/adminReviews'
import { ReviewsApiError } from '../../api/reviews'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import RatingStars from '../reviews/RatingStars'
import AdminDeleteReviewDialog from './AdminDeleteReviewDialog'
import deleteIcon from '../../assets/icons/admin/delete.svg'

type LoadState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: AdminReviewPage }

export default function AdminReviewsPanel() {
  const { t } = useLanguage()
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState('')
  return <section className="admin-panel" aria-labelledby="admin-reviews-title">
    <div className="admin-panel-heading"><h2 id="admin-reviews-title">{t('Reviews')}</h2></div>
    <p className="admin-reviews-description">{t('Moderate reviews of public and private decks. Deleting a review also removes its rating.')}</p>
    {notice && <p className="form-notice form-success" role="status">{t(notice)}</p>}
    <ReviewResults key={`${page}-${revision}`} page={page} onPageChange={setPage}
      onChanged={deleted => { setNotice(deleted ? 'The review has been deleted.' : ''); setRevision(value => value + 1) }} />
  </section>
}

function ReviewResults({ page, onPageChange, onChanged }: {
  page: number; onPageChange: (page: number) => void; onChanged: (deleted: boolean) => void
}) {
  const { t, locale } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<AdminReview | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    getAdminReviews(page, controller.signal).then(data => {
      if (controller.signal.aborted) return
      if (page > 0 && data.content.length === 0) { onPageChange(Math.max(0, data.totalPages - 1)); return }
      setState({ status: 'ready', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof ReviewsApiError && (cause.status === 401 || cause.status === 403)) void refresh()
      setState({ status: 'error', message: cause instanceof ReviewsApiError ? cause.message : 'Unable to load reviews. Please try again.' })
    })
    return () => controller.abort()
  }, [page, attempt, refresh, onPageChange])

  if (state.status === 'loading') return <p className="admin-empty" role="status">{t('Loading reviews…')}</p>
  if (state.status === 'error') return <div className="admin-empty"><p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button></div>
  const { data } = state
  return <>
    <p className="admin-result-count" role="status">{t('Results: {0}', [data.totalElements.toLocaleString(locale)])}</p>
    {data.content.length === 0 ? <div className="admin-empty"><h3>{t('No reviews yet')}</h3></div>
      : <div className="admin-table-scroll" tabIndex={0} role="region" aria-label={t('Reviews table')}><table className="admin-table admin-reviews-table">
        <caption className="db-sr-only">{t('Reviews table')}</caption><thead><tr>{['Deck', 'Author', 'Rating', 'Comment', 'Created', 'Actions'].map(label => <th key={label} scope="col">{t(label)}</th>)}</tr></thead>
        <tbody>{data.content.map(review => <tr key={review.id}>
          <th scope="row">{review.deck.publicDeck ? <Link to={`/explore/${review.deck.id}`}>{review.deck.name}</Link> : <span>{review.deck.name}</span>}
            <span className="admin-review-visibility admin-badge">{review.deck.publicDeck ? t('Public') : t('Private')}</span></th>
          <td className="admin-review-author">{review.author.displayName}</td>
          <td><RatingStars rating={review.rating} /></td>
          <td className="admin-review-comment">{review.comment ?? '—'}</td>
          <td><time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })}</time></td>
          <td><button className="admin-action-button is-danger" type="button" onClick={() => setSelected(review)} aria-haspopup="dialog"
            title={t('Delete review by {0}', [review.author.displayName])} aria-label={t('Delete review by {0}', [review.author.displayName])}>
            <span className="admin-action-icon" aria-hidden="true" style={{ maskImage: `url("${deleteIcon}")` }} />
          </button></td>
        </tr>)}</tbody>
      </table></div>}
    {data.totalPages > 1 && <nav className="admin-pagination" aria-label={t('Review pagination')}>
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>{t('← Previous')}</button>
      <span>{t('Page {0} of {1}', [(page + 1).toLocaleString(locale), data.totalPages.toLocaleString(locale)])}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => onPageChange(page + 1)}>{t('Next →')}</button>
    </nav>}
    {selected && <AdminDeleteReviewDialog review={selected} onCancel={() => setSelected(null)} onDeleted={() => onChanged(true)} onRefresh={() => onChanged(false)} />}
  </>
}
