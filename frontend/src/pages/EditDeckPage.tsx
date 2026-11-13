import { Link, useLocation, useParams } from 'react-router'
import { isOwnedDeck } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import DeckForm from '../components/decks/DeckForm'

export default function EditDeckPage() {
  const { deckId } = useParams()
  const { state } = useLocation()
  const { user } = useAuth()
  const candidate: unknown = state?.deck
  const deck = isOwnedDeck(candidate) && /^\d+$/.test(deckId ?? '') && candidate.id === Number(deckId)
    && state?.ownerId === user?.id ? candidate : null

  return <div className="db-page">
    <Link to="/decks" className="back-link">← Back to My Decks</Link>
    <header className="db-section-heading decks-heading"><div><h1>Edit Deck</h1><p>Update your deck’s details and learning languages.</p></div></header>
    {deck ? <>
      <p className="form-notice deck-preview-note">Preview: editing is coming soon. You can try the form, but changes will not be saved.</p>
      <DeckForm key={deck.id} initialValues={deck} submitLabel="Save changes" pendingLabel="Saving changes…" />
    </> : <section className="decks-empty">
      <h2>Open the deck from My Decks</h2>
      <p>Use the Edit button on your deck to preview this form. Loading a deck from a direct link is coming soon.</p>
      <Link className="button button-primary" to="/decks">Go to My Decks</Link>
    </section>}
  </div>
}
