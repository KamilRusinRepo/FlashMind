export type StatisticsRange = 'day' | 'week' | 'month' | 'all'
export type DailyStatistics = { date: Date; studied: number; correct: number; xp: number }
export type DeckMastery = { id: number; name: string; total: number; mastered: number; masteryPercent: number; correct: number; wrong: number; deckType: 'GENERAL' | 'LANGUAGE'; dueCards: number }
export type StatisticsPeriod = { start: string; end: string; mastered: number; studySeconds: number; studied: number; correct: number; accuracy: number; xp: number }
export type StatisticsOverview = {
  today: string; zone: string
  profile: { totalXp: number; level: number; globalRank: number; streak: number; dailyGoal: number; currentLevelXp: number; nextLevelXp: number }
  periods: Record<StatisticsRange, StatisticsPeriod>
  activity: { date: string; studied: number; correct: number; xp: number }[]
  decks: DeckMastery[]
}
export type MasteryCard = { id: number; question: string; answer: string; masteryLevel: number; lastReviewedAt: string | null; nextReviewAt: string | null }
export type MasteryReport = {
  id: number; name: string; total: number; masteryPercent: number; today: string; zone: string
  buckets: { level: number; count: number }[]; content: MasteryCard[]
  page: number; size: number; totalElements: number; totalPages: number
}
export class StatisticsApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'StatisticsApiError'; this.status = status }
}
const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object'
const integer = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
const date = (value: unknown): value is string => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value))
const instant = (value: unknown) => value === null || typeof value === 'string' && !Number.isNaN(Date.parse(value))
const zone = (value: unknown): value is string => {
  if (typeof value !== 'string') return false
  try { new Intl.DateTimeFormat('en', { timeZone: value }); return true } catch { return false }
}
export function isDeckMastery(value: unknown): value is DeckMastery {
  return object(value) && integer(value.id) && value.id > 0 && typeof value.name === 'string'
    && ['total', 'mastered', 'masteryPercent', 'correct', 'wrong', 'dueCards'].every(key => integer(value[key]))
    && (value.deckType === 'GENERAL' || value.deckType === 'LANGUAGE') && Number(value.dueCards) <= Number(value.total)
    && Number(value.mastered) <= Number(value.total) && Number(value.masteryPercent) <= 100
}
export function isDeckMasteryList(value: unknown): value is DeckMastery[] { return Array.isArray(value) && value.every(isDeckMastery) }
export function isStatisticsOverview(value: unknown): value is StatisticsOverview {
  if (!object(value) || !date(value.today) || !zone(value.zone) || !object(value.profile) || !object(value.periods)) return false
  const profile = value.profile, periods = value.periods
  return ['totalXp', 'level', 'globalRank', 'streak', 'dailyGoal', 'currentLevelXp', 'nextLevelXp'].every(key => integer(profile[key]))
    && Number(profile.nextLevelXp) > 0 && Number(profile.currentLevelXp) <= Number(profile.nextLevelXp)
    && ['day', 'week', 'month', 'all'].every(key => {
      const period = periods[key]
      return object(period) && date(period.start) && date(period.end) && period.start <= period.end
        && ['mastered', 'studySeconds', 'studied', 'correct', 'accuracy', 'xp'].every(field => integer(period[field]))
        && Number(period.correct) <= Number(period.studied) && Number(period.accuracy) <= 100
    }) && Array.isArray(value.activity) && value.activity.length === 365 && value.activity.every(day => object(day)
      && date(day.date) && integer(day.studied) && integer(day.correct) && integer(day.xp) && day.correct <= day.studied)
    && isDeckMasteryList(value.decks)
}
export function isMasteryReport(value: unknown): value is MasteryReport {
  return object(value) && integer(value.id) && value.id > 0 && typeof value.name === 'string' && date(value.today) && zone(value.zone)
    && ['total', 'masteryPercent', 'page', 'size', 'totalElements', 'totalPages'].every(key => integer(value[key]))
    && Number(value.masteryPercent) <= 100 && Number(value.size) > 0 && Number(value.size) <= 100
    && Array.isArray(value.buckets) && value.buckets.length === 4 && value.buckets.every((bucket, index) => object(bucket) && bucket.level === index + 1 && integer(bucket.count))
    && value.buckets.reduce((sum, bucket) => sum + Number(bucket.count), 0) === Number(value.total)
    && Number(value.totalElements) <= Number(value.total)
    && Array.isArray(value.content) && value.content.length <= Number(value.size) && value.content.every(card => object(card)
      && integer(card.id) && card.id > 0 && typeof card.question === 'string' && typeof card.answer === 'string'
      && integer(card.masteryLevel) && card.masteryLevel <= 4 && instant(card.lastReviewedAt) && instant(card.nextReviewAt))
    && value.content.length <= Number(value.totalElements)
    && Number(value.totalPages) === Math.ceil(Number(value.totalElements) / Number(value.size))
}
export async function getStatistics<T>(path: string, validate: (value: unknown) => value is T, signal: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new StatisticsApiError('Unable to load statistics. Please check your connection and try again.') }
  if (!response.ok) throw new StatisticsApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This deck is no longer available.' : 'Unable to load statistics. Please try again.', response.status)
  const data: unknown = await response.json().catch(() => null)
  if (!validate(data)) throw new StatisticsApiError('The server returned unexpected statistics. Please try again.')
  return data
}
// A date-only value is a calendar day, not a UTC instant.
export function statisticsCalendarDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day, 12)
}
