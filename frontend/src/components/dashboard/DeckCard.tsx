import { Link } from 'react-router'
import type { OwnedDeck } from '../../api/decks'
import type { ExploreDeck } from '../../data/dashboard'

export function LearningCard({ deck }: { deck: OwnedDeck }) {
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  return <article className="db-learning-card">
    <div className={`db-learning-icon db-tone-${tone}`} aria-hidden="true">{deck.name.slice(0, 1).toUpperCase()}</div>
    <div className="db-learning-copy">
      <h3>{deck.name}</h3><p>{deck.deckType === 'LANGUAGE' ? 'Language deck' : 'General deck'}</p>
      <p>{deck.sourceLanguage}{deck.targetLanguage && <> → {deck.targetLanguage}</>}</p>
    </div>
    <Link className="db-action" to={`/decks/${deck.id}/edit`} aria-label={`Manage ${deck.name}`}>Manage Deck</Link>
  </article>
}

export default function ExploreDeckCard({ deck }: { deck: ExploreDeck }) {
  return <article className="db-deck-card">
    <div className={`db-deck-cover db-tone-${deck.tone}`} aria-hidden="true"><span>{deck.initial}</span></div>
    <div className="db-deck-body">
      <div className="db-tags"><span>PUBLIC DECK · SAMPLE</span><span className={`db-tone-${deck.tone}`}>{deck.category}</span></div>
      <h3>{deck.name}</h3>
      <p className="db-deck-description">{deck.description}</p>
      <div className="db-explore-meta"><span>{deck.cardCount} cards</span><span>{deck.learners.toLocaleString('en-US')} learners</span><span className="db-rating"><span aria-hidden="true">★ </span>{deck.rating}<span className="db-sr-only"> out of 5</span></span></div>
    </div>
  </article>
}
