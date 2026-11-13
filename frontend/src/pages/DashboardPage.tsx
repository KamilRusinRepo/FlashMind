import DashboardStats from '../components/dashboard/DashboardStats'
import DeckCard, { LearningCard } from '../components/dashboard/DeckCard'
import { continueDecks, exploreDecks, myDecks } from '../data/dashboard'
import { useAuth } from '../auth/AuthContext'
import { Link } from 'react-router'

export default function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null

  return <div className="db-page">
    <div className="db-preview-note"><span className="db-preview-badge">PREVIEW</span><p>You’re exploring a sample dashboard. Decks, streak and XP are example data.</p></div>
    <header className="db-hero">
      <div className="db-welcome"><p className="db-eyebrow">A LITTLE PRACTICE. A LASTING MEMORY.</p>
        <h1>Welcome back, {user.username}!</h1>
        <p>You’re on a roll today. Keep the momentum going.</p>
      </div>
      <DashboardStats />
    </header>
    <section className="db-section" aria-labelledby="continue-title">
      <div className="db-section-heading"><h2 id="continue-title">Continue Learning</h2><button className="db-text-action" type="button" disabled title="Reviews are coming soon">View All Reviews <span aria-hidden="true">→</span></button></div>
      <div className="db-learning-grid">{continueDecks.map(deck => <LearningCard key={deck.id} deck={deck} />)}</div>
    </section>
    <section className="db-section" aria-labelledby="my-decks-title">
      <div className="db-section-heading"><h2 id="my-decks-title">My Decks</h2><Link className="db-create-action" to="/decks/create"><span aria-hidden="true">＋</span> Create Deck</Link></div>
      <div className="db-deck-grid">{myDecks.map(deck => <DeckCard key={deck.id} deck={deck} />)}</div>
    </section>
    <section className="db-section" aria-labelledby="explore-title">
      <div className="db-section-heading"><div><h2 id="explore-title">Explore More</h2><p>A new interest is just a few flashcards away.</p></div><button className="db-text-action" type="button" disabled title="Exploring public decks is coming soon">Explore Marketplace <span aria-hidden="true">→</span></button></div>
      <div className="db-deck-grid">{exploreDecks.map(deck => <DeckCard key={deck.id} deck={deck} discovery />)}</div>
    </section>
    <footer className="db-page-footer">Small steps. Big progress.<span>Sample dashboard · More features coming soon</span></footer>
  </div>
}
