import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useLanguage } from '../i18n/LanguageContext'
import StatisticsCharts from '../components/statistics/StatisticsCharts'
import { createSampleStatistics, currentSampleStreak, sampleStatisticsProfile, summarizeStatistics, type StatisticsRange } from '../data/statistics'
import masteredIcon from '../assets/icons/statistics/mastered_cards.svg'
import timeIcon from '../assets/icons/statistics/study_time.svg'
import accuracyIcon from '../assets/icons/statistics/accuracy.svg'
import './Statistics.css'

const ranges: { value: StatisticsRange; label: string; days?: number }[] = [
  { value: 'day', label: 'Daily', days: 1 },
  { value: 'week', label: 'Weekly', days: 7 },
  { value: 'month', label: 'Monthly', days: 30 },
  { value: 'all', label: 'All-time' },
]

export default function StatisticsPage() {
  const { t, locale, count } = useLanguage()
  const { user } = useAuth()
  const [range, setRange] = useState<StatisticsRange>('all')
  const [sampleDays] = useState(createSampleStatistics)
  const periodDays = ranges.find(period => period.value === range)?.days
  const days = periodDays ? sampleDays.slice(-periodDays) : sampleDays
  const summary = summarizeStatistics(days)
  const totals = summarizeStatistics(sampleDays)
  const streak = currentSampleStreak(sampleDays)
  const number = new Intl.NumberFormat(locale)
  const date = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
  const hours = Math.floor(summary.studyMinutes / 60)
  const minutes = summary.studyMinutes % 60
  const metrics = [
    { title: 'Total Mastered', value: number.format(summary.mastered), hint: 'Cards successfully learned', icon: masteredIcon },
    { title: 'Study Time', value: hours ? t('{0} h {1} min', [number.format(hours), number.format(minutes)]) : t('{0} min', [number.format(minutes)]), hint: 'Time spent in study sessions', icon: timeIcon },
    { title: 'Avg. Accuracy', value: new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(summary.accuracy), hint: 'Correct answers across the period', icon: accuracyIcon },
  ]

  return <div className="statistics-page">
    <header className="statistics-panel statistics-top">
      <h1>{t('FlashMind Stats')}</h1>
      <div className="statistics-range" role="group" aria-label={t('Statistics date range')}>
        {ranges.map(period => <button key={period.value} type="button" aria-pressed={range === period.value} onClick={() => setRange(period.value)}>{t(period.label)}</button>)}
      </div>
    </header>
    <section className="statistics-panel statistics-profile" aria-label={t('Learning profile')}>
      <div className="statistics-profile-left">
        <div className="statistics-profile-badge" aria-label={t('Level {0}', [number.format(sampleStatisticsProfile.level)])}>
          <span aria-hidden="true">★</span><small aria-hidden="true">{t('LVL {0}', [number.format(sampleStatisticsProfile.level)])}</small>
        </div>
        <div><h2>{user?.username}</h2><p>{t('Your study streak: {0}', [count(streak, 'day')])}</p></div>
      </div>
      <dl className="statistics-profile-metrics">
        <div><dt>{t('Global Rank')}</dt><dd>#{number.format(sampleStatisticsProfile.globalRank)}</dd></div>
        <div><dt>{t('Total XP')}</dt><dd>{number.format(totals.xp)}</dd></div>
      </dl>
    </section>
    <p className="statistics-period">{t('Selected period: {0} – {1}', [date.format(days[0].date), date.format(days[days.length - 1].date)])}</p>
    <div className="statistics-metrics">
      {metrics.map(metric => <section className="statistics-panel statistics-metric" key={metric.title}>
        <img className="statistics-metric-icon" src={metric.icon} alt="" aria-hidden="true" />
        <h2>{t(metric.title)}</h2><p className="statistics-metric-value">{metric.value}</p><p className="statistics-metric-hint">{t(metric.hint)}</p>
      </section>)}
    </div>
    <StatisticsCharts days={sampleDays} streak={streak} />
  </div>
}
