import { Link } from 'react-router'
import { useLanguage } from '../i18n/LanguageContext'
import { sampleDeckMastery } from '../data/statistics'
import { createSampleMasteryCards } from '../data/mastery'
import './MasteryReport.css'

export default function DeckStatisticsPage() {
  const { t, locale } = useLanguage()
  const number = new Intl.NumberFormat(locale)
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 })
  return <div className="mastery-report-page">
    <header className="mastery-report-header">
      <div><h1>{t('Statistics')}</h1><p>{t('Choose a deck to inspect mastery, weak cards, and next review dates.')}</p></div>
      <Link className="button button-outline" to="/statistics">{t('Stats Overview')}</Link>
    </header>
    <p className="mastery-report-preview">{t('Sample data only. Your learning activity is not connected yet.')}</p>
    <div className="mastery-deck-grid">{sampleDeckMastery.map(deck => {
      const good = createSampleMasteryCards(deck).filter(card => card.level >= 3).length
      return <Link className="mastery-deck-card" to={`/statistics/decks/${deck.slug}`} key={deck.slug}>
        <span className="mastery-deck-initial" aria-hidden="true">{deck.name[0]}</span>
        <div><h2>{deck.name}</h2><p>{t('{0} cards · {1} good · {2} weak', [number.format(deck.total), number.format(good), number.format(deck.total - good)])}</p>
          <progress max={deck.total} value={deck.mastered} aria-label={t('Mastery of {0}', [deck.name])} />
          <div className="mastery-deck-footer"><span>{t('Mastery')}</span><strong>{percent.format(deck.mastered / deck.total)}</strong></div>
        </div>
      </Link>
    })}</div>
  </div>
}
