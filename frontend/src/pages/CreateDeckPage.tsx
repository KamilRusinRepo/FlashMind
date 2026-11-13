import { Link, useNavigate } from 'react-router'
import { createDeck } from '../api/decks'
import DeckForm from '../components/decks/DeckForm'

export default function CreateDeckPage() {
  const navigate = useNavigate()
  return <div className="db-page">
    <Link to="/decks" className="back-link">← Back to My Decks</Link>
    <header className="db-section-heading decks-heading"><div><h1>Create New Deck</h1><p>Organize your learning with a custom set of flashcards.</p></div></header>
    <DeckForm save={createDeck} onSaved={deck => navigate('/decks', { replace: true, state: { createdDeckId: deck.id } })} />
  </div>
}
