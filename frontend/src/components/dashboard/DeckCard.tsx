import { useLanguage } from '../../i18n/LanguageContext'
import { Link } from 'react-router'
import type { DeckMastery } from '../../api/statistics'
import StudyStartButton from '../study/StudyStartButton'

export function LearningCard({ deck }: { deck: DeckMastery }) {
  const { t, locale, count } = useLanguage()
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  return <article className="db-learning-card">
    <div className={`db-learning-icon db-tone-${tone}`} aria-hidden="true">{deck.name.slice(0, 1).toUpperCase()}</div>
    <div className="db-learning-copy">
      <h3><Link className="db-learning-deck-link" to={`/decks/${deck.id}`}>{deck.name}</Link></h3><p>{t('{0} ready to study today', [count(deck.dueCards, 'card')])}</p>
      <div className="db-progress-label"><span>{t('Mastery')}</span><strong>{new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(deck.masteryPercent / 100)}</strong></div>
      <progress className="db-progress" max={100} value={deck.masteryPercent} aria-label={t('Mastery of {0}', [deck.name])} />
    </div>
    <div className="db-learning-start">{deck.total > 0 ? <StudyStartButton deckId={deck.id} label={t('Start Session')} />
      : <button className="button button-primary" type="button" disabled title={t('Add at least one flashcard before starting a study session.')}>{t('Start Session')}</button>}</div>
  </article>
}
