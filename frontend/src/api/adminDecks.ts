import { requestWithCsrf } from './csrf'
import { isAdminSummary, type AdminUserSummary } from './adminUsers'
import { isPublicDeck, type PublicExploreDeck } from './explore'

export type AdminDeckFilters = { search: string; deckType: string }
export type AdminDeckPage = { content: PublicExploreDeck[]; page: number; size: number; totalElements: number; totalPages: number; summary: AdminUserSummary }

export class AdminDecksApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'AdminDecksApiError'; this.status = status }
}

export async function getAdminDecks(filters: AdminDeckFilters, page: number, signal: AbortSignal): Promise<AdminDeckPage> {
  const params = new URLSearchParams({ search: filters.search, deck_type: filters.deckType, page: String(page), size: '5' })
  let response: Response
  try {
    response = await fetch(`/api/admin/decks?${params}`, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new AdminDecksApiError('Unable to load public decks. Please check your connection and try again.') }
  if (!response.ok) throw new AdminDecksApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 403 ? 'You do not have permission to access this resource.' : 'Unable to load public decks. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (response.status !== 200 || !data || !Array.isArray(data.content) || !data.content.every(isPublicDeck) || data.content.length > 5
    || data.page !== page || data.size !== 5 || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / 5)
    || !isAdminSummary(data.summary) || data.totalElements > data.summary.publicDecks) {
    throw new AdminDecksApiError('The server returned an unexpected public deck list. Please try again.')
  }
  return data as AdminDeckPage
}

export async function deleteAdminDeck(deckId: number): Promise<void> {
  let response: Response
  try {
    response = await requestWithCsrf(`/api/admin/decks/${deckId}`, 'DELETE', { signal: AbortSignal.timeout(15000) })
  } catch { throw new AdminDecksApiError('Unable to confirm deletion. Refresh the public deck list before trying again.') }
  if (response.status === 204) return
  throw new AdminDecksApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 403 ? 'You do not have permission to access this resource.'
      : response.status === 404 ? 'This public deck is no longer available. Refresh the list.' : 'Unable to delete this public deck. Please try again.', response.status)
}
