import type { DashboardDeck, ExploreDeck } from '../../data/dashboard'

export function LearningCard({ deck }: { deck: DashboardDeck }) {
  return <article className="db-learning-card">
    <div className={`db-learning-icon db-tone-${deck.tone}`} aria-hidden="true">{deck.initial}</div>
    <div className="db-learning-copy">
      <h3>{deck.name}</h3><p>{deck.due} cards due for review</p>
      <div className="db-progress-label"><span>Mastery</span><span>{deck.mastery}%</span></div>
      <progress className="db-progress" value={deck.mastery} max={100} aria-label={`${deck.name} mastery`} />
    </div>
    <button className="db-action" type="button" disabled title="Study sessions are coming soon">Start Session</button>
  </article>
}

type DeckCardProps = { deck: DashboardDeck; discovery?: never } | { deck: ExploreDeck; discovery: true }

export default function DeckCard(props: DeckCardProps) {
  const { deck } = props
  return <article className="db-deck-card">
    <div className={`db-deck-cover db-tone-${deck.tone}`} aria-hidden="true"><span>{deck.initial}</span></div>
    <div className="db-deck-body">
      <div className="db-tags"><span>{props.discovery ? 'PUBLIC DECK' : 'MY DECK'}</span><span className={`db-tone-${deck.tone}`}>{deck.category}</span></div>
      <h3>{deck.name}</h3>
      {props.discovery ? <>
        <p className="db-deck-description">{props.deck.description}</p>
        <div className="db-explore-meta"><span>{deck.cardCount} cards</span><span>{props.deck.learners.toLocaleString('en-US')} learners</span><span className="db-rating"><span aria-hidden="true">★ </span>{props.deck.rating}<span className="db-sr-only"> out of 5</span></span></div>
      </> : <>
        <p>{deck.cardCount} Cards</p>
        <progress className="db-progress" value={deck.mastery} max={100} aria-label={`${deck.name} mastery`} />
        <div className="db-deck-footer"><span>Mastery</span><strong>{deck.mastery}%</strong></div>
      </>}
    </div>
  </article>
}
