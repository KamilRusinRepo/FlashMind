import { isOwnedDeck, type OwnedDeck } from './decks'
import { postWithCsrf } from './csrf'

export type StudyQuestion = { id: number; flashcardId: number | null; position: number; question: string; exampleSentence: string | null; imageUrl: string | null }
export type StudySessionData = { id: number; deck: OwnedDeck; totalCards: number; status: 'ACTIVE' | 'COMPLETED' | 'ABANDONED'; startedAt: string; cards: StudyQuestion[] }

export class StudyApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'StudyApiError'; this.status = status }
}

const positiveId = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0

function isQuestion(value: unknown, index: number): value is StudyQuestion {
  if (!value || typeof value !== 'object') return false
  const card = value as Record<string, unknown>
  return positiveId(card.id) && (card.flashcardId === null || positiveId(card.flashcardId)) && card.position === index + 1
    && typeof card.question === 'string' && card.question.trim().length > 0
    && (card.exampleSentence === null || typeof card.exampleSentence === 'string')
    && (card.imageUrl === null || typeof card.imageUrl === 'string')
}

function readSession(value: unknown): StudySessionData {
  if (!value || typeof value !== 'object') throw new StudyApiError('The server returned an unexpected study session.')
  const data = value as Record<string, unknown>
  if (!positiveId(data.id) || !isOwnedDeck(data.deck) || typeof data.totalCards !== 'number' || !Number.isSafeInteger(data.totalCards)
    || data.totalCards < 1 || data.totalCards > 10
    || !['ACTIVE', 'COMPLETED', 'ABANDONED'].includes(String(data.status))
    || typeof data.startedAt !== 'string' || Number.isNaN(Date.parse(data.startedAt))
    || !Array.isArray(data.cards) || data.cards.length !== data.totalCards || !data.cards.every(isQuestion)
    || new Set(data.cards.map(card => card.id)).size !== data.cards.length) {
    throw new StudyApiError('The server returned an unexpected study session.')
  }
  return value as StudySessionData
}

async function responseError(response: Response, fallback: string): Promise<StudyApiError> {
  const data = await response.json().catch(() => null)
  return new StudyApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This deck or study session is unavailable or does not belong to your account.'
      : response.status === 409 && typeof data?.message === 'string' ? data.message : fallback, response.status)
}

export async function startStudySession(deckId: number): Promise<StudySessionData> {
  let response: Response
  try {
    response = await postWithCsrf(`/api/decks/${deckId}/study-sessions`, { signal: AbortSignal.timeout(15000) })
  } catch { throw new StudyApiError('Unable to confirm whether the study session started. Check your connection before trying again.') }
  if (!response.ok) throw await responseError(response, 'Unable to start the study session. Please try again.')
  const session = readSession(await response.json().catch(() => null))
  if (response.status !== 201 || session.deck.id !== deckId || session.status !== 'ACTIVE') throw new StudyApiError('The study session could not be confirmed.')
  return session
}

export async function getStudySession(sessionId: number, signal: AbortSignal): Promise<StudySessionData> {
  let response: Response
  try {
    response = await fetch(`/api/study-sessions/${sessionId}`, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new StudyApiError('Unable to load the study session. Check your connection and try again.') }
  if (!response.ok) throw await responseError(response, 'Unable to load the study session. Please try again.')
  const session = readSession(await response.json().catch(() => null))
  if (session.id !== sessionId) throw new StudyApiError('The server returned an unexpected study session.')
  return session
}
