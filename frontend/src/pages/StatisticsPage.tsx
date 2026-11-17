import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useLanguage } from '../i18n/LanguageContext'
import StatisticsCharts from '../components/statistics/StatisticsCharts'
import { isStatisticsOverview, statisticsCalendarDate, type StatisticsRange, type StatisticsOverview } from '../api/statistics'
import { useStatisticsResource } from '../components/statistics/useStatisticsResource'
import StatisticsStatus from '../components/statistics/StatisticsStatus'
import masteredIcon from '../assets/icons/statistics/mastered_cards.svg'
import timeIcon from '../assets/icons/statistics/study_time.svg'
import accuracyIcon from '../assets/icons/statistics/accuracy.svg'
import './Statistics.css'

const ranges: { value: StatisticsRange; label: string }[] = [
  { value: 'day', label: 'Daily' },
  { value: 'week', label: 'Weekly' },
  { value: 'month', label: 'Monthly' },
  { value: 'all', label: 'All-time' },
]

export default function StatisticsPage() {
  const { state, retry } = useStatisticsResource('/api/statistics/me', isStatisticsOverview)
  if (state.status !== 'ready') return <StatisticsStatus message={state.status === 'error' ? state.message : undefined} retry={retry} />
  return <StatisticsView data={state.data} />
}

function StatisticsView({ data }: { data: StatisticsOverview }) {
  const { t, locale, count } = useLanguage()
  const { user } = useAuth()
  const [range, setRange] = useState<StatisticsRange>('all')
  const summary = data.periods[range]
  const { streak, level, globalRank, totalXp } = data.profile
  const days = data.activity.map(day => ({ ...day, date: statisticsCalendarDate(day.date) }))
  const number = new Intl.NumberFormat(locale)
  const date = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
  const hours = Math.floor(summary.studySeconds / 3600)
  const minutes = Math.floor(summary.studySeconds / 60) % 60
  const studyTime = hours ? t('{0} h {1} min', [number.format(hours), number.format(minutes)])
    : summary.studySeconds < 60 ? t('{0} s', [number.format(summary.studySeconds)]) : t('{0} min', [number.format(minutes)])
  const metrics = [
    { title: 'Total Mastered', value: number.format(summary.mastered), hint: 'Cards successfully learned', icon: masteredIcon },
    { title: 'Study Time', value: studyTime, hint: 'Time spent in study sessions', icon: timeIcon },
    { title: 'Avg. Accuracy', value: new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(summary.accuracy / 100), hint: 'Correct answers across the period', icon: accuracyIcon },
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
        <div className="statistics-profile-badge" aria-label={t('Level {0}', [number.format(level)])}>
          <span aria-hidden="true">★</span><small aria-hidden="true">{t('LVL {0}', [number.format(level)])}</small>
        </div>
        <div><h2>{user?.username}</h2><p>{t('Your study streak: {0}', [count(streak, 'day')])}</p></div>
      </div>
      <dl className="statistics-profile-metrics">
        <div><dt>{t('Global Rank')}</dt><dd>#{number.format(globalRank)}</dd></div>
        <div><dt>{t('Total XP')}</dt><dd>{number.format(totalXp)}</dd></div>
      </dl>
    </section>
    <p className="statistics-period">{t('Selected period: {0} – {1}', [date.format(statisticsCalendarDate(summary.start)), date.format(statisticsCalendarDate(summary.end))])}</p>
    <div className="statistics-metrics">
      {metrics.map(metric => <section className="statistics-panel statistics-metric" key={metric.title}>
        <img className="statistics-metric-icon" src={metric.icon} alt="" aria-hidden="true" />
        <h2>{t(metric.title)}</h2><p className="statistics-metric-value">{metric.value}</p><p className="statistics-metric-hint">{t(metric.hint)}</p>
      </section>)}
    </div>
    {data.periods.all.studied === 0 && <p className="statistics-preview">{t('No learning activity yet. Start a study session to see your progress.')}</p>}
    <p className="statistics-preview">{t('Study streak requires {0} answers per day.', [data.profile.dailyGoal])}</p>
    <StatisticsCharts days={days} streak={streak} decks={data.decks} />
  </div>
}
