import { Link } from 'react-router'
import { useLanguage } from '../i18n/LanguageContext'
import { isDeckMasteryList } from '../api/statistics'
import { useStatisticsResource } from '../components/statistics/useStatisticsResource'
import StatisticsStatus from '../components/statistics/StatisticsStatus'
import './MasteryReport.css'

export default function DeckStatisticsPage() {
  const { t, locale } = useLanguage()
  const { state, retry } = useStatisticsResource('/api/statistics/me/decks', isDeckMasteryList)
  const number = new Intl.NumberFormat(locale)
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 })
  return <div className="mastery-report-page">
    <header className="mastery-report-header">
      <div><h1>{t('Statistics')}</h1><p>{t('Choose a deck to inspect mastery, weak cards, and next review dates.')}</p></div>
      <Link className="button button-outline" to="/statistics">{t('Stats Overview')}</Link>
    </header>
    {state.status !== 'ready' ? <StatisticsStatus message={state.status === 'error' ? state.message : undefined} retry={retry} />
      : state.data.length === 0 ? <p className="mastery-report-empty">{t('No decks yet. Create a deck to see its mastery report.')}</p>
      : <div className="mastery-deck-grid">{state.data.map(deck => {
      return <Link className="mastery-deck-card" to={`/statistics/decks/${deck.id}`} key={deck.id}>
        <span className="mastery-deck-initial" aria-hidden="true">{deck.name[0]}</span>
        <div><h2>{deck.name}</h2><p>{t('{0} cards · {1} correct · {2} wrong', [number.format(deck.total), number.format(deck.correct), number.format(deck.wrong)])}</p>
          <progress max={100} value={deck.masteryPercent} aria-label={t('Mastery of {0}', [deck.name])} />
          <div className="mastery-deck-footer"><span>{t('Mastery')}</span><strong>{percent.format(deck.masteryPercent / 100)}</strong></div>
        </div>
      </Link>
    })}</div>}
  </div>
}
