import { useLanguage } from '../../i18n/LanguageContext'
import { categoryName } from '../../i18n/labels'
import type { DeckMastery } from '../../api/statistics'
import type { ExploreDeck } from '../../data/dashboard'
import StudyStartButton from '../study/StudyStartButton'

export function LearningCard({ deck }: { deck: DeckMastery }) {
  const { t, locale, count } = useLanguage()
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  return <article className="db-learning-card">
    <div className={`db-learning-icon db-tone-${tone}`} aria-hidden="true">{deck.name.slice(0, 1).toUpperCase()}</div>
    <div className="db-learning-copy">
      <h3>{deck.name}</h3><p>{t('{0} ready to study today', [count(deck.dueCards, 'card')])}</p>
      <div className="db-progress-label"><span>{t('Mastery')}</span><strong>{new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(deck.masteryPercent / 100)}</strong></div>
      <progress className="db-progress" max={100} value={deck.masteryPercent} aria-label={t('Mastery of {0}', [deck.name])} />
    </div>
    <div className="db-learning-start">{deck.total > 0 ? <StudyStartButton deckId={deck.id} label={t('Start Session')} />
      : <button className="button button-primary" type="button" disabled title={t('Add at least one flashcard before starting a study session.')}>{t('Start Session')}</button>}</div>
  </article>
}

export default function ExploreDeckCard({ deck }: { deck: ExploreDeck }) {
  const { t, locale, count } = useLanguage()
  return <article className="db-deck-card">
    <div className={`db-deck-cover db-tone-${deck.tone}`} aria-hidden="true"><span>{deck.initial}</span></div>
    <div className="db-deck-body">
      <div className="db-tags"><span>{t("PUBLIC DECK · SAMPLE")}</span><span className={`db-tone-${deck.tone}`}>{categoryName(deck.category, t)}</span></div>
      <h3>{deck.name}</h3>
      <p className="db-deck-description">{deck.description}</p>
      <div className="db-explore-meta"><span>{count(deck.cardCount, 'card')}</span><span>{count(deck.learners, 'learner')}</span><span className="db-rating"><span aria-hidden="true">★ </span>{Number(deck.rating).toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}<span className="db-sr-only"> {t("out of 5")}</span></span></div>
    </div>
  </article>
}
