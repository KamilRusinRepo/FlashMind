export type OwnedDeck = {
  id: number
  name: string
  description: string | null
  deckType: 'GENERAL' | 'LANGUAGE'
  sourceLanguage: string
  targetLanguage: string | null
  category: string | null
  publicDeck: boolean
  createdAt: string
}

export type DeckPage = { content: OwnedDeck[]; page: number; size: number; totalElements: number; totalPages: number }

export class DeckListError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'DeckListError'; this.status = status }
}

function isDeck(value: unknown): value is OwnedDeck {
  if (!value || typeof value !== 'object') return false
  const deck = value as Record<string, unknown>
  return typeof deck.id === 'number' && Number.isSafeInteger(deck.id) && deck.id > 0 && typeof deck.name === 'string'
    && (deck.description === null || typeof deck.description === 'string')
    && (deck.deckType === 'GENERAL' || deck.deckType === 'LANGUAGE')
    && typeof deck.sourceLanguage === 'string' && (deck.targetLanguage === null || typeof deck.targetLanguage === 'string')
    && (deck.category === null || typeof deck.category === 'string') && typeof deck.publicDeck === 'boolean'
    && typeof deck.createdAt === 'string' && !Number.isNaN(Date.parse(deck.createdAt))
}

export async function getMyDecks(page: number, search: string, signal: AbortSignal): Promise<DeckPage> {
  let response: Response
  try {
    const params = new URLSearchParams({ page: String(page), size: '12', search })
    response = await fetch(`/api/decks?${params}`, {
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch {
    throw new DeckListError('Unable to load your decks. Please check your connection and try again.')
  }
  if (!response.ok) throw new DeckListError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : 'Unable to load your decks. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (!data || !Array.isArray(data.content) || !data.content.every(isDeck)
    || data.page !== page || data.size !== 12 || data.content.length > data.size
    || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / data.size)) {
    throw new DeckListError('The server returned an unexpected deck list. Please try again.')
  }
  return data as DeckPage
}
