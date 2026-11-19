import { requestWithCsrf } from './csrf'
import { ReviewsApiError } from './reviews'

export type AdminReview = {
  id: number; deck: { id: number; name: string; publicDeck: boolean }; author: { id: number; displayName: string }
  rating: number; comment: string | null; createdAt: string; updatedAt: string
}
export type AdminReviewPage = { content: AdminReview[]; page: number; size: number; totalElements: number; totalPages: number }

function isReview(value: unknown): value is AdminReview {
  if (!value || typeof value !== 'object') return false
  const row = value as AdminReview
  return Number.isSafeInteger(row.id) && row.id > 0 && Number.isInteger(row.rating) && row.rating >= 1 && row.rating <= 5
    && !!row.author && Number.isSafeInteger(row.author.id) && row.author.id > 0 && typeof row.author.displayName === 'string'
    && !!row.deck && Number.isSafeInteger(row.deck.id) && row.deck.id > 0 && typeof row.deck.name === 'string' && typeof row.deck.publicDeck === 'boolean'
    && (row.comment === null || typeof row.comment === 'string')
    && typeof row.createdAt === 'string' && !Number.isNaN(Date.parse(row.createdAt))
    && typeof row.updatedAt === 'string' && !Number.isNaN(Date.parse(row.updatedAt))
}

export async function getAdminReviews(page: number, signal: AbortSignal): Promise<AdminReviewPage> {
  let response: Response
  try {
    response = await fetch(`/api/admin/reviews?${new URLSearchParams({ page: String(page), size: '5' })}`, {
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch { throw new ReviewsApiError('Unable to load reviews. Check your connection and try again.') }
  if (!response.ok) throw new ReviewsApiError(accessError(response.status, 'Unable to load reviews. Please try again.'), response.status)
  const data = await response.json().catch(() => null)
  if (response.status !== 200 || !data || !Array.isArray(data.content) || !data.content.every(isReview) || data.content.length > 5
    || new Set(data.content.map((row: AdminReview) => row.id)).size !== data.content.length
    || data.page !== page || data.size !== 5 || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || data.totalPages !== Math.ceil(data.totalElements / 5)) {
    throw new ReviewsApiError('The server returned an unexpected review list. Please try again.')
  }
  return data as AdminReviewPage
}

export async function deleteAdminReview(reviewId: number, signal: AbortSignal): Promise<void> {
  const uncertain = 'Unable to confirm deletion. Refresh reviews before trying again.'
  let response: Response
  try {
    response = await requestWithCsrf(`/api/admin/reviews/${reviewId}`, 'DELETE', { signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new ReviewsApiError(uncertain, 0, {}, true) }
  if (response.status === 204) return
  throw new ReviewsApiError(accessError(response.status, response.status === 404 ? 'This review is no longer available. Refresh the list.'
    : response.status >= 500 || response.ok ? uncertain : 'Unable to delete this review. Please try again.'), response.status, {}, response.status >= 500 || response.ok)
}

function accessError(status: number, fallback: string): string {
  return status === 401 ? 'Your session has expired. Please sign in again.'
    : status === 403 ? 'You do not have permission to access this resource.' : fallback
}
