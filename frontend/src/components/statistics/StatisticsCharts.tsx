import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { useLanguage } from '../../i18n/LanguageContext'
import type { DailyStatistics, DeckMastery } from '../../api/statistics'
import StatisticsTooltip from './StatisticsTooltip'
import { useStatisticsTooltip } from './useStatisticsTooltip'

export default function StatisticsCharts({ days, streak, decks }: { days: DailyStatistics[]; streak: number; decks: DeckMastery[] }) {
  const { t, locale, count } = useLanguage()
  const chartId = useId()
  const tooltipId = `${chartId}-tooltip`
  const tooltip = useStatisticsTooltip()
  const [focusedDay, setFocusedDay] = useState(days.length - 1)
  const heatCells = useRef<(HTMLButtonElement | null)[]>([])
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
  const dayLabel = (day: DailyStatistics) => `${date.format(day.date)}: ${count(day.studied, 'card')}, ${number.format(day.xp)} XP`
  function moveDay(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const steps: Record<string, number> = { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -1, ArrowDown: 1 }
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? days.length - 1 : event.key in steps ? index + steps[event.key] : null
    if (next === null) return
    event.preventDefault()
    const bounded = Math.max(0, Math.min(days.length - 1, next))
    setFocusedDay(bounded)
    heatCells.current[bounded]?.focus()
  }

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
            <div className="statistics-heatmap" style={grid} role="group" aria-label={t('Daily study activity')} aria-describedby={`${chartId}-heat-help`}>
              {days.map((day, index) => <button type="button" key={day.date.getTime()} className={`statistics-heat-day statistics-heat-${heatLevel(day)}`}
                ref={node => { heatCells.current[index] = node }} tabIndex={index === focusedDay ? 0 : -1}
                style={{ gridColumn: Math.floor((offset + index) / 7) + 1, gridRow: (offset + index) % 7 + 1 }}
                aria-label={dayLabel(day)} aria-describedby={tooltip.active?.key === `heat-${index}` ? tooltipId : undefined}
                onPointerEnter={event => { if (event.pointerType !== 'touch') tooltip.open(day, event.currentTarget, `heat-${index}`) }}
                onPointerLeave={event => { if (document.activeElement !== event.currentTarget) tooltip.closeLater() }}
                onFocus={event => { setFocusedDay(index); tooltip.open(day, event.currentTarget, `heat-${index}`) }}
                onBlur={tooltip.closeLater} onKeyDown={event => moveDay(event, index)}
                onClick={event => tooltip.open(day, event.currentTarget, `heat-${index}`)} />)}
            </div>
          </div>
        </div>
      </div>
      <p className="db-sr-only" id={`${chartId}-heat-help`}>{t('Use arrow keys to move between days.')}</p>
      <div className="statistics-streak-note"><strong>{t('Study streak: {0}', [count(streak, 'day')])}</strong><span>{t('You are building a steady learning rhythm. Keep it up!')}</span></div>
    </section>
    <div className="statistics-lower-grid">
      <section className="statistics-panel" aria-labelledby={`${chartId}-performance`}>
        <div className="statistics-panel-heading"><div><h2 id={`${chartId}-performance`}>{t('Weekly Performance')}</h2><p>{t('XP earned each day over the last 7 days.')}</p></div></div>
        <div className="statistics-week-bars" role="group" aria-label={t('Weekly XP chart. Daily values are available in the chart data table below.')}>
          {week.map((day, index) => <button type="button" className={`statistics-week-day${index === week.length - 1 ? ' is-today' : ''}`} key={day.date.getTime()}
            aria-label={dayLabel(day)} aria-describedby={tooltip.active?.key === `week-${index}` ? tooltipId : undefined}
            onPointerEnter={event => { if (event.pointerType !== 'touch') tooltip.open(day, event.currentTarget, `week-${index}`) }}
            onPointerLeave={event => { if (document.activeElement !== event.currentTarget) tooltip.closeLater() }}
            onFocus={event => tooltip.open(day, event.currentTarget, `week-${index}`)} onBlur={tooltip.closeLater}
            onClick={event => tooltip.open(day, event.currentTarget, `week-${index}`)}>
            <span className="statistics-week-xp" aria-hidden="true">{number.format(day.xp)}</span>
            <span className="statistics-week-track" aria-hidden="true"><span className="statistics-week-bar" style={{ height: `${day.xp / maxXp * 100}%` }} /></span>
            <strong aria-hidden="true">{weekday.format(day.date)}</strong>
          </button>)}
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
    <StatisticsTooltip id={tooltipId} active={tooltip.active} onEnter={tooltip.keepOpen} onLeave={tooltip.closeLater} />
  </>
}
