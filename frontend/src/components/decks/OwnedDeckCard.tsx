import type { OwnedDeck } from '../../api/decks'

export default function OwnedDeckCard({ deck }: { deck: OwnedDeck }) {
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  return <article className="db-deck-card decks-list-card">
    <div className={`db-deck-cover db-tone-${tone}`} aria-hidden="true"><span>{deck.name.slice(0, 1).toUpperCase()}</span></div>
    <div className="db-deck-body">
      <div className="db-tags"><span>{deck.publicDeck ? 'PUBLIC' : 'PRIVATE'}</span><span className={`db-tone-${tone}`}>{deck.category || 'Uncategorized'}</span></div>
      <h3>{deck.name}</h3>
      <p className="db-deck-description">{deck.description || 'No description yet.'}</p>
      <div className="decks-language"><span>{deck.deckType === 'LANGUAGE' ? 'Language deck' : 'General deck'}</span>
        <strong>{deck.sourceLanguage}{deck.targetLanguage && <> <span aria-hidden="true">→</span> {deck.targetLanguage}</>}</strong>
      </div>
      <div className="db-deck-footer"><span>Created</span><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}</time></div>
    </div>
  </article>
}
