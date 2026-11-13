export type Flashcard = {
  id: number; question: string; answer: string; exampleSentence: string | null
  translatedExample: string | null; imageUrl: string | null; createdAt: string
}
export type FlashcardPage = { content: Flashcard[]; page: number; size: number; totalElements: number; totalPages: number }

export class FlashcardApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'FlashcardApiError'; this.status = status }
}

function isFlashcard(value: unknown): value is Flashcard {
  if (!value || typeof value !== 'object') return false
  const card = value as Record<string, unknown>
  return typeof card.id === 'number' && Number.isSafeInteger(card.id) && card.id > 0
    && typeof card.question === 'string' && typeof card.answer === 'string'
    && ['exampleSentence', 'translatedExample', 'imageUrl'].every(field => card[field] === null || typeof card[field] === 'string')
    && typeof card.createdAt === 'string' && !Number.isNaN(Date.parse(card.createdAt))
}

export async function getFlashcards(deckId: number, page: number, signal: AbortSignal): Promise<FlashcardPage> {
  let response: Response
  try {
    response = await fetch(`/api/decks/${deckId}/flashcards?${new URLSearchParams({ page: String(page), size: '12' })}`, {
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    })
  } catch { throw new FlashcardApiError('Unable to load flashcards. Check your connection and try again.') }
  if (!response.ok) throw new FlashcardApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This deck is unavailable or does not belong to your account.'
      : 'Unable to load flashcards. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (!data || !Array.isArray(data.content) || !data.content.every(isFlashcard)
    || data.page !== page || data.size !== 12 || data.content.length > data.size
    || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / data.size)) {
    throw new FlashcardApiError('The server returned an unexpected flashcard list. Please try again.')
  }
  return data as FlashcardPage
}
