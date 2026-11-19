import { isFlashcard, type FlashcardPage } from './flashcards'
import { requestWithCsrf } from './csrf'
import { isRatingSummary } from './reviews'

export type PublicExploreDeck = {
  id: number; name: string; description: string | null; deckType: 'GENERAL' | 'LANGUAGE'
  category: string | null; sourceLanguage: string; targetLanguage: string | null; backgroundUrl: string | null
  createdAt: string; cardCount: number; author: { id: number | null; displayName: string }; ownDeck: boolean
  following: boolean; followerCount: number; averageRating: number | null; reviewCount: number
}
export type PublicDeckPage = { content: PublicExploreDeck[]; page: number; size: number; totalElements: number; totalPages: number }
export class ExploreApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'ExploreApiError'; this.status = status }
}
export function isPublicDeck(value: unknown): value is PublicExploreDeck {
  if (!value || typeof value !== 'object') return false
  const deck = value as Record<string, unknown>
  const author = deck.author as Record<string, unknown> | null
  const optionalText = (text: unknown) => text === null || typeof text === 'string'
  return Number.isSafeInteger(deck.id) && Number(deck.id) > 0 && typeof deck.name === 'string' && optionalText(deck.description)
    && (deck.deckType === 'GENERAL' || deck.deckType === 'LANGUAGE') && optionalText(deck.category)
    && typeof deck.sourceLanguage === 'string' && optionalText(deck.targetLanguage) && optionalText(deck.backgroundUrl)
    && typeof deck.createdAt === 'string' && !Number.isNaN(Date.parse(deck.createdAt))
    && Number.isSafeInteger(deck.cardCount) && Number(deck.cardCount) >= 0 && typeof deck.ownDeck === 'boolean'
    && typeof deck.following === 'boolean' && Number.isSafeInteger(deck.followerCount) && Number(deck.followerCount) >= 0
    && isRatingSummary(deck)
    && !!author && typeof author === 'object' && (author.id === null || Number.isSafeInteger(author.id) && Number(author.id) > 0) && typeof author.displayName === 'string'
}
export async function getPublicDecks(query: string, page: number, signal: AbortSignal): Promise<PublicDeckPage> {
  const params = new URLSearchParams(query)
  params.set('size', '12')
  let response: Response
  try {
    response = await fetch(`/api/explore/decks?${params}`, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new ExploreApiError('Unable to load public decks. Please check your connection and try again.') }
  if (!response.ok) throw new ExploreApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 400 ? 'These search filters are invalid. Clear the filters and try again.' : 'Unable to load public decks. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (response.status !== 200 || !data || !Array.isArray(data.content) || !data.content.every(isPublicDeck) || data.content.length > 12
    || data.page !== page || data.size !== 12 || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / 12)) {
    throw new ExploreApiError('The server returned an unexpected public deck list. Please try again.')
  }
  return data as PublicDeckPage
}

async function getPublicResource(path: string, signal: AbortSignal): Promise<unknown> {
  let response: Response
  try {
    response = await fetch(`/api/explore/decks/${path}`, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new ExploreApiError('Unable to load this public deck. Check your connection and try again.') }
  if (!response.ok) throw new ExploreApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This public deck is no longer available.' : 'Unable to load this public deck. Please try again.', response.status)
  if (response.status !== 200) throw new ExploreApiError('The server returned unexpected public deck data. Please try again.')
  return response.json().catch(() => null)
}

export async function setDeckFollowing(deckId: number, following: boolean): Promise<PublicExploreDeck | null> {
  let response: Response
  try {
    response = await requestWithCsrf(`/api/explore/decks/${deckId}/follow`, following ? 'PUT' : 'DELETE', { signal: AbortSignal.timeout(15000) })
  } catch { throw new ExploreApiError('Unable to confirm following changes. Refresh the page before trying again.') }
  if (!response.ok) throw new ExploreApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This public deck is no longer available.'
      : response.status === 409 ? 'You cannot follow your own deck.' : 'Unable to change deck following. Please try again.', response.status)
  if (!following && response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (response.status !== 200 || !isPublicDeck(data) || data.id !== deckId || !data.following) {
    throw new ExploreApiError('Unable to confirm following changes. Refresh the page before trying again.')
  }
  return data
}

export async function getPublicDeck(deckId: number, signal: AbortSignal): Promise<PublicExploreDeck> {
  const data = await getPublicResource(String(deckId), signal)
  if (!isPublicDeck(data) || data.id !== deckId) throw new ExploreApiError('The server returned unexpected public deck data. Please try again.')
  return data
}

export async function getPublicFlashcards(deckId: number, page: number, signal: AbortSignal): Promise<FlashcardPage> {
  const value = await getPublicResource(`${deckId}/flashcards?${new URLSearchParams({ page: String(page), size: '12' })}`, signal)
  const data = value as FlashcardPage | null
  if (!data || !Array.isArray(data.content) || !data.content.every(isFlashcard) || data.content.length > 12
    || data.page !== page || data.size !== 12 || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / 12)) {
    throw new ExploreApiError('The server returned an unexpected flashcard list. Please try again.')
  }
  return data
}
