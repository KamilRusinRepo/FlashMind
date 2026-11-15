export type StatisticsRange = 'day' | 'week' | 'month' | 'all'

export type DailyStatistics = {
  date: Date
  studied: number
  correct: number
  sessions: number
  mastered: number
  studyMinutes: number
  xp: number
}

// Preview fixtures only. These numbers do not describe the signed-in user's activity.
export function createSampleStatistics(today = new Date()): DailyStatistics[] {
  return Array.from({ length: 365 }, (_, index) => {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 364 + index)
    const active = index >= 358 || (index % 9 !== 0 && index % 9 !== 1)
    const studied = active ? 12 + (index * 17) % 49 : 0
    return {
      date,
      studied,
      correct: Math.round(studied * (0.65 + (index % 7) * 0.04)),
      sessions: active ? 1 + index % 3 : 0,
      mastered: active ? 1 + index % 4 : 0,
      studyMinutes: active ? 5 + index % 26 : 0,
      xp: active ? studied * 5 + (index % 3) * 10 : 0,
    }
  })
}

export function summarizeStatistics(days: DailyStatistics[]) {
  const studied = days.reduce((sum, day) => sum + day.studied, 0)
  const correct = days.reduce((sum, day) => sum + day.correct, 0)
  const sessions = days.reduce((sum, day) => sum + day.sessions, 0)
  const mastered = days.reduce((sum, day) => sum + day.mastered, 0)
  const studyMinutes = days.reduce((sum, day) => sum + day.studyMinutes, 0)
  const xp = days.reduce((sum, day) => sum + day.xp, 0)
  return { studied, correct, sessions, mastered, studyMinutes, xp, accuracy: studied ? correct / studied : 0 }
}

export function currentSampleStreak(days: DailyStatistics[]) {
  let streak = 0
  for (let index = days.length - 1; index >= 0 && days[index].studied > 0; index--) streak++
  return streak
}

export const sampleStatisticsProfile = { level: 6, globalRank: 12 }

export const sampleDeckMastery = [
  { name: 'English A1', mastered: 42, total: 60 },
  { name: 'English B1', mastered: 23, total: 50 },
  { name: 'Spanish Basics', mastered: 8, total: 40 },
]
