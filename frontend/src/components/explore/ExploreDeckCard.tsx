import { useLanguage } from '../../i18n/LanguageContext'
import { categoryName, languageName } from '../../i18n/labels'
import type { ExploreDeckPreview } from '../../data/explore'
import followIcon from '../../assets/icons/explore/follow.svg'

export default function ExploreDeckCard({ deck }: { deck: ExploreDeckPreview }) {
  const { t, count, locale } = useLanguage()
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : deck.category === 'Science' ? 'green' : deck.category === 'Tech' ? 'orange' : 'blue'
  return <article className="explore-deck-card">
    <div className={`explore-deck-cover db-tone-${tone}`} aria-hidden="true"><span>{deck.name.slice(0, 1).toUpperCase()}</span></div>
    <div className="explore-deck-body">
      <div className="db-tags"><span>{deck.deckType === 'LANGUAGE' ? t('Language') : t('General')}</span><span className={`db-tone-${tone}`}>{categoryName(deck.category, t)}</span></div>
      <h3>{deck.name}</h3>
      <p className="explore-deck-author">{t('By {0}', [deck.author ?? t('Deleted user')])}</p>
      <p className="explore-deck-description">{deck.description}</p>
      <p className="explore-deck-languages">{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> → {languageName(deck.targetLanguage, t)}</>}</p>
      <div className="explore-deck-meta"><span>{count(deck.cardCount, 'card')}</span><span>{count(deck.learners, 'learner')}</span>
        <span className="explore-deck-rating"><span aria-hidden="true">★ </span>{new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(deck.rating)}<span className="db-sr-only"> {t('out of 5')}</span></span>
      </div>
    </div>
    <div className="explore-deck-actions"><button className="explore-follow-button" type="button" disabled title={t('Following decks will be available after backend integration.')}>
      <span className="explore-follow-icon" aria-hidden="true" style={{ maskImage: `url("${followIcon}")` }} /><span>{t('Follow Deck')}</span>
    </button></div>
  </article>
}
