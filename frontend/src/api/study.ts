import { isOwnedDeck, type OwnedDeck } from './decks'
import { postWithCsrf } from './csrf'

export type StudyMode = 'SCHEDULED_REVIEW' | 'FREE_PRACTICE'
const isMode = (value: unknown): value is StudyMode => value === 'SCHEDULED_REVIEW' || value === 'FREE_PRACTICE'

export type StudyResult = { userAnswer: string; correct: boolean; usedHint: boolean; skipped: boolean; answeredAt: string; answer: string; translatedExample: string | null }
export type StudyQuestion = { id: number; flashcardId: number | null; position: number; question: string; exampleSentence: string | null; imageUrl: string | null; usedHint: boolean; hint: string | null; result: StudyResult | null }
export type StudySessionData = { id: number; deck: OwnedDeck; totalCards: number; status: 'ACTIVE' | 'COMPLETED' | 'ABANDONED'; startedAt: string; cards: StudyQuestion[]; mode: StudyMode }
export type StudySummaryData = { sessionId: number; deckId: number; deckName: string; totalCards: number; correctCards: number; wrongCards: number; accuracy: number; xpEarned: number; durationSeconds: number; startedAt: string; endedAt: string; mode: StudyMode }

function readSummary(value: unknown, sessionId: number): StudySummaryData {
  if (!value || typeof value !== 'object') throw new StudyApiError('Unable to confirm the session summary. Refresh the session.')
  const data = value as StudySummaryData
  if (data.sessionId !== sessionId || !positiveId(data.deckId) || typeof data.deckName !== 'string'
    || ![data.totalCards, data.correctCards, data.wrongCards, data.accuracy, data.xpEarned, data.durationSeconds].every(value => Number.isSafeInteger(value) && value >= 0)
    || !isMode(data.mode) || data.totalCards < 1 || (data.mode === 'SCHEDULED_REVIEW' && data.totalCards > 10) || data.correctCards + data.wrongCards !== data.totalCards
    || data.accuracy !== Math.round(100 * data.correctCards / data.totalCards) || data.xpEarned > data.correctCards * 10
    || typeof data.startedAt !== 'string' || typeof data.endedAt !== 'string'
    || !Number.isFinite(Date.parse(data.startedAt)) || !Number.isFinite(Date.parse(data.endedAt)) || Date.parse(data.endedAt) < Date.parse(data.startedAt)) {
    throw new StudyApiError('Unable to confirm the session summary. Refresh the session.')
  }
  return data
}

export async function completeStudySession(sessionId: number): Promise<StudySummaryData> {
  let response: Response
  try { response = await postWithCsrf(`/api/study-sessions/${sessionId}/complete`, { signal: AbortSignal.timeout(15000) }) }
  catch { throw new StudyApiError('Completion could not be confirmed. Refresh the session to see its saved state.') }
  if (!response.ok) throw await responseError(response, 'Unable to complete the session. Please try again or refresh the session.')
  return readSummary(await response.json().catch(() => null), sessionId)
}

export async function getStudySummary(sessionId: number, signal: AbortSignal): Promise<StudySummaryData> {
  let response: Response
  try {
    response = await fetch(`/api/study-sessions/${sessionId}/summary`, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new StudyApiError('Unable to load the summary. Check your connection and try again.') }
  if (!response.ok) throw await responseError(response, 'Unable to load the summary. Please try again.')
  return readSummary(await response.json().catch(() => null), sessionId)
}

export class StudyApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'StudyApiError'; this.status = status }
}

const positiveId = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0

function isResult(value: unknown): value is StudyResult {
  if (!value || typeof value !== 'object') return false
  const result = value as Record<string, unknown>
  return typeof result.userAnswer === 'string' && typeof result.correct === 'boolean' && typeof result.usedHint === 'boolean'
    && typeof result.skipped === 'boolean' && (!result.skipped || (!result.correct && result.userAnswer === ''))
    && typeof result.answeredAt === 'string' && !Number.isNaN(Date.parse(result.answeredAt))
    && typeof result.answer === 'string' && (result.translatedExample === null || typeof result.translatedExample === 'string')
}

function isQuestion(value: unknown, index?: number): value is StudyQuestion {
  if (!value || typeof value !== 'object') return false
  const card = value as Record<string, unknown>
  return positiveId(card.id) && (card.flashcardId === null || positiveId(card.flashcardId))
    && positiveId(card.position) && (index === undefined || card.position === index + 1)
    && typeof card.question === 'string' && card.question.trim().length > 0
    && (card.exampleSentence === null || typeof card.exampleSentence === 'string')
    && (card.imageUrl === null || typeof card.imageUrl === 'string')
    && typeof card.usedHint === 'boolean' && (card.hint === null || typeof card.hint === 'string')
    && (card.result === null ? card.usedHint === (typeof card.hint === 'string')
      : isResult(card.result) && card.result.usedHint === card.usedHint && card.hint === null)
}

export async function submitStudyAnswer(sessionId: number, answerId: number, userAnswer: string, skipped: boolean): Promise<StudyQuestion> {
  return mutateQuestion(sessionId, answerId, { userAnswer, skipped })
}

export async function requestStudyHint(sessionId: number, answerId: number): Promise<StudyQuestion> {
  return mutateQuestion(sessionId, answerId)
}

async function mutateQuestion(sessionId: number, answerId: number, body?: { userAnswer: string; skipped: boolean }): Promise<StudyQuestion> {
  let response: Response
  try {
    response = await postWithCsrf(`/api/study-sessions/${sessionId}/answers/${answerId}${body === undefined ? '/hint' : ''}`, {
      headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000),
    })
  } catch { throw new StudyApiError('The save could not be confirmed. Refresh the session before trying again.') }
  if (!response.ok) throw await responseError(response, 'Unable to save this action. Please try again or refresh the session.')
  const data: unknown = await response.json().catch(() => null)
  if (response.status !== 200 || !isQuestion(data) || data.id !== answerId
    || (body === undefined ? data.result !== null || !data.usedHint : data.result === null)) {
    throw new StudyApiError('The action could not be confirmed. Refresh the session to see the saved state.')
  }
  return data
}

function readSession(value: unknown): StudySessionData {
  if (!value || typeof value !== 'object') throw new StudyApiError('The server returned an unexpected study session.')
  const data = value as Record<string, unknown>
  if (!positiveId(data.id) || !isOwnedDeck(data.deck) || typeof data.totalCards !== 'number' || !Number.isSafeInteger(data.totalCards)
    || !isMode(data.mode) || data.totalCards < 1 || (data.mode === 'SCHEDULED_REVIEW' && data.totalCards > 10)
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

export async function startStudySession(deckId: number, mode: StudyMode): Promise<StudySessionData> {
  let response: Response
  try {
    response = await postWithCsrf(`/api/decks/${deckId}/study-sessions`, {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode }), signal: AbortSignal.timeout(15000),
    })
  } catch { throw new StudyApiError('Unable to confirm whether the study session started. Check your connection before trying again.') }
  if (!response.ok) throw await responseError(response, 'Unable to start the study session. Please try again.')
  const session = readSession(await response.json().catch(() => null))
  if (response.status !== 201 || session.deck.id !== deckId || session.status !== 'ACTIVE' || session.mode !== mode) throw new StudyApiError('The study session could not be confirmed.')
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
