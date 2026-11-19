import { useLanguage } from '../../i18n/LanguageContext'
import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { categoryName, languageName } from '../../i18n/labels'
import type { PublicExploreDeck } from '../../api/explore'
import FollowDeckButton from './FollowDeckButton'
import DeckRating from '../reviews/DeckRating'

export default function ExploreDeckCard({ deck: initialDeck }: { deck: PublicExploreDeck }) {
  const [deck, setDeck] = useState(initialDeck)
  const { t, count, locale } = useLanguage()
  const { search } = useLocation()
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : deck.category === 'Science' ? 'green' : deck.category === 'Tech' ? 'orange' : 'blue'
  return <article className="explore-deck-card">
    <div className={`explore-deck-cover db-tone-${tone}`} aria-hidden="true">{deck.backgroundUrl && failedImage !== deck.backgroundUrl
      ? <img src={deck.backgroundUrl} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedImage(deck.backgroundUrl)} />
      : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}</div>
    <div className="explore-deck-body">
      <div className="db-tags"><span>{deck.deckType === 'LANGUAGE' ? t('Language') : t('General')}</span><span className={`db-tone-${tone}`}>{deck.category ? categoryName(deck.category, t) : t('Uncategorized')}</span></div>
      <h3><Link className="explore-deck-link" to={`/explore/${deck.id}${search}`}>{deck.name}</Link></h3>
      <p className="explore-deck-author">{t('By {0}', [deck.author.id === null ? t('Deleted user') : deck.author.displayName])}</p>
      {deck.description?.trim() && <p className="explore-deck-description">{deck.description}</p>}
      <p className="explore-deck-languages">{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> → {languageName(deck.targetLanguage, t)}</>}</p>
      <div className="explore-deck-meta">
        <span>{count(deck.cardCount, 'card')}</span>
        <span>{t('Followers: {0}', [deck.followerCount.toLocaleString(locale)])}</span>
        <DeckRating averageRating={deck.averageRating} reviewCount={deck.reviewCount} />
      </div>
    </div>
    <div className="explore-deck-actions"><FollowDeckButton deckId={deck.id} following={deck.following} ownDeck={deck.ownDeck}
      followerCount={deck.followerCount} onChanged={next => setDeck(value => ({ ...value, ...next }))} /></div>
  </article>
}
