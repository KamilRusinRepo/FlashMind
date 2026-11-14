import { useLanguage } from '../../i18n/LanguageContext'
import { categoryName, languageName } from '../../i18n/labels'
import { Link } from 'react-router'
import type { OwnedDeck } from '../../api/decks'
import type { ExploreDeck } from '../../data/dashboard'

export function LearningCard({ deck }: { deck: OwnedDeck }) {
  const { t } = useLanguage()
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  return <article className="db-learning-card">
    <div className={`db-learning-icon db-tone-${tone}`} aria-hidden="true">{deck.name.slice(0, 1).toUpperCase()}</div>
    <div className="db-learning-copy">
      <h3>{deck.name}</h3><p>{deck.deckType === 'LANGUAGE' ? t("Language deck") : t("General deck")}</p>
      <p>{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> → {languageName(deck.targetLanguage, t)}</>}</p>
    </div>
    <Link className="db-action" to={`/decks/${deck.id}/edit`} aria-label={t("Manage {0}", [deck.name])}>{t("Manage Deck")}</Link>
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
