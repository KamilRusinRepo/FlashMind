import { useState } from 'react'
import { Link } from 'react-router'
import { useLanguage } from '../../i18n/LanguageContext'
import { categoryName, languageName } from '../../i18n/labels'
import type { OwnedDeck } from '../../api/decks'
import type { DeckMastery } from '../../api/statistics'

export default function DashboardDeckCard({ deck, mastery, masteryLoading }: {
  deck: OwnedDeck; mastery?: DeckMastery; masteryLoading: boolean
}) {
  const { t, locale, count } = useLanguage()
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  return <article className="db-deck-card decks-list-card db-dashboard-deck">
    <div className={`db-deck-cover db-tone-${tone}`} aria-hidden="true">{deck.backgroundUrl && failedImage !== deck.backgroundUrl
      ? <img src={deck.backgroundUrl} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedImage(deck.backgroundUrl)} />
      : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}</div>
    <div className="db-deck-body">
      <div className="db-tags"><span>{deck.publicDeck ? t('PUBLIC') : t('PRIVATE')}</span><span className={`db-tone-${tone}`}>{deck.category ? categoryName(deck.category, t) : t('Uncategorized')}</span></div>
      <h3><Link className="deck-details-link" to={`/decks/${deck.id}`} aria-label={t('Open {0}', [deck.name])}>{deck.name}</Link></h3>
      <div className="db-dashboard-deck-meta">
        {mastery && <span>{count(mastery.total, 'card')}</span>}
        <span>{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> → {languageName(deck.targetLanguage, t)}</>}</span>
      </div>
      {mastery ? <div className="db-dashboard-deck-mastery">
        <progress className="db-progress" max={100} value={mastery.masteryPercent} aria-label={t('Mastery of {0}', [deck.name])} />
        <div className="db-progress-label"><span>{t('Mastery')}</span><strong>{new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(mastery.masteryPercent / 100)}</strong></div>
      </div> : <p className="db-dashboard-deck-status" role="status">{masteryLoading ? t('Loading mastery…') : t('Mastery is temporarily unavailable.')}</p>}
    </div>
  </article>
}
