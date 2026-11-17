import { requestWithCsrf } from './csrf'

export type OwnedDeck = {
  id: number
  name: string
  description: string | null
  deckType: 'GENERAL' | 'LANGUAGE'
  sourceLanguage: string
  targetLanguage: string | null
  category: string | null
  backgroundUrl: string | null
  publicDeck: boolean
  createdAt: string
  ownDeck: boolean
  author: { id: number | null; displayName: string }
}

export type DeckPage = { content: OwnedDeck[]; page: number; size: number; totalElements: number; totalPages: number }

export function isOwnedDeck(value: unknown): value is OwnedDeck {
  if (!value || typeof value !== 'object') return false
  const deck = value as Record<string, unknown>
  const author = deck.author as Record<string, unknown> | null
  return typeof deck.id === 'number' && Number.isSafeInteger(deck.id) && deck.id > 0 && typeof deck.name === 'string'
    && (deck.description === null || typeof deck.description === 'string')
    && (deck.deckType === 'GENERAL' || deck.deckType === 'LANGUAGE')
    && typeof deck.sourceLanguage === 'string' && (deck.targetLanguage === null || typeof deck.targetLanguage === 'string')
    && (deck.category === null || typeof deck.category === 'string') && typeof deck.publicDeck === 'boolean'
    && (deck.backgroundUrl === null || typeof deck.backgroundUrl === 'string')
    && typeof deck.createdAt === 'string' && !Number.isNaN(Date.parse(deck.createdAt))
    && typeof deck.ownDeck === 'boolean' && !!author && typeof author === 'object'
    && (author.id === null || Number.isSafeInteger(author.id) && Number(author.id) > 0) && typeof author.displayName === 'string'
}

export async function getMyDecks(page: number, search: string, signal: AbortSignal): Promise<DeckPage> {
  return getDeckPage(page, search, signal, false)
}

export async function getLibraryDecks(page: number, search: string, signal: AbortSignal): Promise<DeckPage> {
  return getDeckPage(page, search, signal, true)
}

async function getDeckPage(page: number, search: string, signal: AbortSignal, library: boolean): Promise<DeckPage> {
  let response: Response
  try {
    const params = new URLSearchParams({ page: String(page), size: '12', search })
    response = await fetch(`/api/decks${library ? '/library' : ''}?${params}`, {
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch {
    throw new DeckApiError('Unable to load your decks. Please check your connection and try again.')
  }
  if (!response.ok) throw new DeckApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : 'Unable to load your decks. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (!data || !Array.isArray(data.content) || !data.content.every(isOwnedDeck)
    || data.page !== page || data.size !== 12 || data.content.length > data.size
    || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / data.size)) {
    throw new DeckApiError('The server returned an unexpected deck list. Please try again.')
  }
  return data as DeckPage
}

export type SaveDeckRequest = {
  name: string; description: string | null; deckType: OwnedDeck['deckType']; sourceLanguage: string
  targetLanguage: string | null; category: string | null; backgroundUrl: string | null; publicDeck: boolean
}
export type DeckField = Exclude<keyof SaveDeckRequest, 'publicDeck'>
export type DeckFieldErrors = Partial<Record<DeckField, string>>

export class DeckApiError extends Error {
  readonly status: number
  readonly fieldErrors: DeckFieldErrors
  constructor(message: string, status = 0, fieldErrors: DeckFieldErrors = {}) {
    super(message); this.name = 'DeckApiError'; this.status = status; this.fieldErrors = fieldErrors
  }
}

export function createDeck(request: SaveDeckRequest): Promise<OwnedDeck> {
  return saveDeck('/api/decks', 'POST', request, 201)
}

export function updateDeck(id: number, request: SaveDeckRequest): Promise<OwnedDeck> {
  return saveDeck(`/api/decks/${id}`, 'PUT', request, 200)
}

async function saveDeck(path: string, method: 'POST' | 'PUT', request: SaveDeckRequest, expectedStatus: number): Promise<OwnedDeck> {
  let response: Response
  try {
    response = await requestWithCsrf(path, method, {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request), signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new DeckApiError('Unable to confirm saving. Reload My Decks before trying again, then check your connection.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const fieldErrors: DeckFieldErrors = {}
    const fields: DeckField[] = ['name', 'description', 'deckType', 'sourceLanguage', 'targetLanguage', 'category', 'backgroundUrl']
    for (const field of fields) if (typeof data?.fieldErrors?.[field] === 'string') fieldErrors[field] = data.fieldErrors[field]
    throw new DeckApiError(Object.keys(fieldErrors).length ? 'Please check the highlighted fields.'
      : response.status === 404 ? 'This deck is no longer available. Return to My Decks.'
      : response.status === 401 ? 'Your session has expired. Please sign in again.' : 'Unable to save your deck. Please try again.', response.status, fieldErrors)
  }
  if (response.status !== expectedStatus || !isOwnedDeck(data)) throw new DeckApiError('The server returned an unexpected response. Reload My Decks to check your changes.')
  return data
}

export async function getOwnedDeck(id: number, signal: AbortSignal): Promise<OwnedDeck> {
  return loadDeck(id, signal, false)
}

export async function getStudyDeck(id: number, signal: AbortSignal): Promise<OwnedDeck> {
  return loadDeck(id, signal, true)
}

async function loadDeck(id: number, signal: AbortSignal, studyAccess: boolean): Promise<OwnedDeck> {
  let response: Response
  try {
    response = await fetch(`/api/decks/${id}${studyAccess ? '/study-access' : ''}`, {
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch { throw new DeckApiError('Unable to load this deck. Please check your connection and try again.') }
  if (!response.ok) throw new DeckApiError(response.status === 404 ? 'This deck is no longer available.'
    : response.status === 401 ? 'Your session has expired. Please sign in again.' : 'Unable to load this deck. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (response.status !== 200 || !isOwnedDeck(data) || data.id !== id) throw new DeckApiError('The server returned an unexpected deck. Please try again.')
  return data
}

export async function deleteDeck(id: number): Promise<void> {
  let response: Response
  try {
    response = await requestWithCsrf(`/api/decks/${id}`, 'DELETE', { signal: AbortSignal.timeout(15000) })
  } catch { throw new DeckApiError('Unable to confirm deletion. Refresh My Decks before trying again, then check your connection.') }
  if (response.status === 204) return
  throw new DeckApiError(response.status === 404 ? 'This deck is no longer available. Refresh the list.'
    : response.status === 401 ? 'Your session has expired. Please sign in again.' : 'Unable to delete your deck. Please try again.', response.status)
}
