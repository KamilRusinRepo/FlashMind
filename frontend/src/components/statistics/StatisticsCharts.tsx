import { useId } from 'react'
import { Link } from 'react-router'
import { useLanguage } from '../../i18n/LanguageContext'
import type { DailyStatistics, DeckMastery } from '../../api/statistics'

export default function StatisticsCharts({ days, streak, decks }: { days: DailyStatistics[]; streak: number; decks: DeckMastery[] }) {
  const { t, locale, count } = useLanguage()
  const chartId = useId()
  const number = new Intl.NumberFormat(locale)
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 })
  const date = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
  const month = new Intl.DateTimeFormat(locale, { month: 'short' })
  const weekday = new Intl.DateTimeFormat(locale, { weekday: 'short' })
  const offset = (days[0].date.getDay() + 6) % 7
  const columns = Math.ceil((offset + days.length) / 7)
  const grid = { gridTemplateColumns: `repeat(${columns}, minmax(10px, 1fr))` }
  const week = days.slice(-7)
  const maxXp = Math.max(1, ...week.map(day => day.xp))
  const heatLevel = (day: DailyStatistics) => day.studied === 0 ? 0 : Math.min(5, Math.ceil(day.studied / 12))
  const tooltip = (day: DailyStatistics) => `${date.format(day.date)}: ${count(day.studied, 'card')}`

  return <>
    <section className="statistics-panel statistics-consistency" aria-labelledby={`${chartId}-consistency`}>
      <div className="statistics-panel-heading">
        <div><h2 id={`${chartId}-consistency`}>{t('Learning Consistency')}</h2><p>{t('Your activity over the last 365 days')}</p></div>
        <div className="statistics-heat-legend" aria-hidden="true"><span>{t('Less')}</span>{[1, 2, 3, 4, 5].map(level => <i key={level} className={`statistics-heat-${level}`} />)}<span>{t('More')}</span></div>
      </div>
      <div className="statistics-heat-scroll" tabIndex={0} role="region" aria-label={t('Learning activity calendar. Daily values are available in the chart data table below.')}>
        <div className="statistics-heat-calendar">
          <div className="statistics-heat-months" style={grid} aria-hidden="true">
            {days.flatMap((day, index) => day.date.getDate() === 1 ? [<span key={day.date.getTime()} style={{ gridColumn: Math.floor((offset + index) / 7) + 1 }}>{month.format(day.date)}</span>] : [])}
          </div>
          <div className="statistics-heat-body">
            <div className="statistics-heat-weekdays" aria-hidden="true">{Array.from({ length: 7 }, (_, index) => <span key={index}>{index % 2 === 0 && index < 5 ? weekday.format(new Date(2026, 0, 5 + index)) : ''}</span>)}</div>
            <div className="statistics-heatmap" style={grid} role="img" aria-label={t('Daily study activity')}>
              {days.map((day, index) => <span key={day.date.getTime()} className={`statistics-heat-day statistics-heat-${heatLevel(day)}`}
                style={{ gridColumn: Math.floor((offset + index) / 7) + 1, gridRow: (offset + index) % 7 + 1 }} title={tooltip(day)} />)}
            </div>
          </div>
        </div>
      </div>
      <div className="statistics-streak-note"><strong>{t('Study streak: {0}', [count(streak, 'day')])}</strong><span>{t('You are building a steady learning rhythm. Keep it up!')}</span></div>
    </section>
    <div className="statistics-lower-grid">
      <section className="statistics-panel" aria-labelledby={`${chartId}-performance`}>
        <div className="statistics-panel-heading"><div><h2 id={`${chartId}-performance`}>{t('Weekly Performance')}</h2><p>{t('XP earned each day over the last 7 days.')}</p></div></div>
        <div className="statistics-week-bars" role="img" aria-label={t('Weekly XP chart. Daily values are available in the chart data table below.')}>
          {week.map((day, index) => <div className={`statistics-week-day${index === week.length - 1 ? ' is-today' : ''}`} key={day.date.getTime()} title={`${date.format(day.date)}: ${number.format(day.xp)} XP`}>
            <span className="statistics-week-xp" aria-hidden="true">{number.format(day.xp)}</span>
            <div className="statistics-week-track" aria-hidden="true"><div className="statistics-week-bar" style={{ height: `${day.xp / maxXp * 100}%` }} /></div>
            <strong aria-hidden="true">{weekday.format(day.date)}</strong>
          </div>)}
        </div>
      </section>
      <section className="statistics-panel statistics-mastery" aria-labelledby={`${chartId}-mastery`}>
        <div className="statistics-panel-heading"><h2 id={`${chartId}-mastery`}>{t('Deck Mastery')}</h2></div>
        {decks.length === 0 && <p className="statistics-preview">{t('No decks yet. Create a deck to see its mastery report.')}</p>}
        <div className="statistics-mastery-list">{decks.map(deck => <Link className="statistics-mastery-deck" to={`/statistics/decks/${deck.id}`} key={deck.id}>
          <div><h3>{deck.name}</h3><strong>{percent.format(deck.masteryPercent / 100)}</strong></div>
          <p>{t('Cards mastered: {0}', [number.format(deck.mastered)])}</p>
          <progress max={100} value={deck.masteryPercent} aria-label={t('Mastery of {0}', [deck.name])} />
        </Link>)}</div>
        <Link to="/statistics/decks" className="statistics-view-decks">{t('View All Decks')} <span aria-hidden="true">→</span></Link>
      </section>
    </div>
    <details className="statistics-panel statistics-data">
      <summary>{t('View chart data')}</summary>
      <div className="statistics-table-scroll" role="region" aria-label={t('Chart data')} tabIndex={0}>
        <table><caption className="db-sr-only">{t('Daily study activity')}</caption>
          <thead><tr><th scope="col">{t('Period')}</th><th scope="col">{t('Cards studied')}</th><th scope="col">{t('Correct answers')}</th><th scope="col">{t('XP')}</th></tr></thead>
          <tbody>{days.toReversed().map(day => <tr key={day.date.getTime()}><th scope="row">{date.format(day.date)}</th><td>{number.format(day.studied)}</td><td>{number.format(day.correct)}</td><td>{number.format(day.xp)}</td></tr>)}</tbody>
        </table>
      </div>
    </details>
  </>
}
