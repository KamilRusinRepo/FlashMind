import { FlashcardApiError } from './flashcards'

export async function exportFlashcardsCsv(deckId: number, signal: AbortSignal): Promise<Blob> {
  let response: Response
  try {
    response = await fetch(`/api/decks/${deckId}/flashcards/export`, {
      credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'text/csv, application/json' },
      signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
    })
  } catch { throw new FlashcardApiError('Unable to export flashcards. Check your connection and try again.') }
  if (!response.ok) throw new FlashcardApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This deck is unavailable or does not belong to your account.'
      : 'Unable to export flashcards. Please try again.', response.status)
  if (response.status !== 200 || response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'text/csv') {
    throw new FlashcardApiError('The server returned an unexpected CSV file. Please try again.')
  }
  try { return await response.blob() }
  catch { throw new FlashcardApiError('Unable to export flashcards. Check your connection and try again.') }
}
