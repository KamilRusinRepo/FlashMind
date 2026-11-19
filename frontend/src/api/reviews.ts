import { postWithCsrf } from './csrf'

export type RatingSummary = { averageRating: number | null; reviewCount: number }
export type Review = { id: number; author: { id: number; displayName: string }; rating: number; comment: string | null; createdAt: string; updatedAt: string; ownReview: boolean }
export type ReviewSummary = RatingSummary & { distribution: { rating: number; count: number }[] }
export type ReviewPage = { content: Review[]; page: number; size: number; totalElements: number; totalPages: number; summary: ReviewSummary; ownReview: Review | null }
export type ReviewFieldErrors = Partial<Record<'rating' | 'comment', string>>

export class ReviewsApiError extends Error {
  readonly status: number
  readonly fieldErrors: ReviewFieldErrors
  readonly uncertain: boolean
  constructor(message: string, status = 0, fieldErrors: ReviewFieldErrors = {}, uncertain = false) {
    super(message); this.name = 'ReviewsApiError'; this.status = status; this.fieldErrors = fieldErrors; this.uncertain = uncertain
  }
}

export function isRatingSummary(value: unknown): value is RatingSummary {
  if (!value || typeof value !== 'object') return false
  const item = value as RatingSummary
  return Number.isSafeInteger(item.reviewCount) && item.reviewCount >= 0
    && (item.reviewCount === 0 ? item.averageRating === null : typeof item.averageRating === 'number' && Number.isFinite(item.averageRating) && item.averageRating >= 1 && item.averageRating <= 5)
}

function isReview(value: unknown): value is Review {
  if (!value || typeof value !== 'object') return false
  const review = value as Review
  return Number.isSafeInteger(review.id) && review.id > 0 && Number.isInteger(review.rating) && review.rating >= 1 && review.rating <= 5
    && !!review.author && Number.isSafeInteger(review.author.id) && review.author.id > 0 && typeof review.author.displayName === 'string'
    && (review.comment === null || typeof review.comment === 'string') && typeof review.ownReview === 'boolean'
    && typeof review.createdAt === 'string' && !Number.isNaN(Date.parse(review.createdAt))
    && typeof review.updatedAt === 'string' && !Number.isNaN(Date.parse(review.updatedAt))
}

export async function getDeckReviews(deckId: number, page: number, signal: AbortSignal, owned = false): Promise<ReviewPage> {
  let response: Response
  try {
    response = await fetch(`${owned ? '/api/decks' : '/api/explore/decks'}/${deckId}/reviews?${new URLSearchParams({ page: String(page), size: '5' })}`, {
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch { throw new ReviewsApiError('Unable to load reviews. Check your connection and try again.') }
  if (!response.ok) throw new ReviewsApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 403 ? 'You do not have permission to access this resource.'
      : response.status === 404 ? (owned ? 'This deck is unavailable or does not belong to your account.' : 'This public deck is no longer available.') : 'Unable to load reviews. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  const summary = data?.summary
  if (response.status !== 200 || !data || !Array.isArray(data.content) || !data.content.every(isReview) || data.content.length > 5
    || new Set(data.content.map((review: Review) => review.id)).size !== data.content.length
    || data.page !== page || data.size !== 5 || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || data.totalPages !== Math.ceil(data.totalElements / 5) || !isRatingSummary(summary) || summary.reviewCount !== data.totalElements
    || (data.ownReview !== null && (!isReview(data.ownReview) || !data.ownReview.ownReview))) {
    throw new ReviewsApiError('The server returned an unexpected review list. Please try again.')
  }
  const buckets: unknown = data.summary.distribution
  if (!Array.isArray(buckets) || buckets.length !== 5 || !buckets.every((bucket, index) => bucket && bucket.rating === 5 - index
    && Number.isSafeInteger(bucket.count) && bucket.count >= 0)
    || buckets.reduce((sum, bucket) => sum + bucket.count, 0) !== summary.reviewCount
    || (summary.reviewCount > 0 && Math.abs(buckets.reduce((sum, bucket) => sum + bucket.rating * bucket.count, 0) / summary.reviewCount - Number(summary.averageRating)) > 1e-9)) {
    throw new ReviewsApiError('The server returned an unexpected review list. Please try again.')
  }
  return data as ReviewPage
}

export async function createDeckReview(deckId: number, rating: number, comment: string, signal: AbortSignal): Promise<Review> {
  const request = { rating, comment: comment.trim() || null }
  const uncertain = 'Unable to confirm your review. Refresh reviews before trying again.'
  let response: Response
  try {
    response = await postWithCsrf(`/api/explore/decks/${deckId}/reviews`, {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request), signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch { throw new ReviewsApiError(uncertain, 0, {}, true) }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const fields: ReviewFieldErrors = {}
    for (const field of ['rating', 'comment'] as const) if (typeof data?.fieldErrors?.[field] === 'string') fields[field] = data.fieldErrors[field]
    const conflict = response.status === 409 && ['You cannot review your own deck.', 'You have already reviewed this deck.'].includes(data?.message)
    throw new ReviewsApiError(Object.keys(fields).length ? 'Please check the highlighted fields.'
      : response.status === 401 ? 'Your session has expired. Please sign in again.'
        : response.status === 403 ? 'You do not have permission to access this resource.'
          : response.status === 404 ? 'This public deck is no longer available.'
            : conflict ? data.message : response.status >= 500 ? uncertain : 'Unable to add your review. Please try again.', response.status, fields, response.status >= 500)
  }
  if (response.status !== 201 || !isReview(data) || !data.ownReview || data.rating !== request.rating || data.comment !== request.comment) {
    throw new ReviewsApiError(uncertain, response.status, {}, true)
  }
  return data
}
