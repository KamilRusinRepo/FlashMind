import type { OwnedDeck } from '../../api/decks'
import { useState } from 'react'
import { Link } from 'react-router'
import DeleteDeckDialog from './DeleteDeckDialog'

export default function OwnedDeckCard({ deck, onChanged }: { deck: OwnedDeck; onChanged: (deleted: boolean) => void }) {
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  return <><article className="db-deck-card decks-list-card">
    <div className={`db-deck-cover db-tone-${tone}`} aria-hidden="true">{deck.backgroundUrl && failedImage !== deck.backgroundUrl
      ? <img src={deck.backgroundUrl} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedImage(deck.backgroundUrl)} />
      : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}</div>
    <div className="db-deck-body">
      <div className="db-tags"><span>{deck.publicDeck ? 'PUBLIC' : 'PRIVATE'}</span><span className={`db-tone-${tone}`}>{deck.category || 'Uncategorized'}</span></div>
      <h3><Link className="deck-details-link" to={`/decks/${deck.id}`} aria-label={`Open ${deck.name}`}>{deck.name}</Link></h3>
      <p className="db-deck-description">{deck.description || 'No description yet.'}</p>
      <div className="decks-language"><span>{deck.deckType === 'LANGUAGE' ? 'Language deck' : 'General deck'}</span>
        <strong>{deck.sourceLanguage}{deck.targetLanguage && <> <span aria-hidden="true">→</span> {deck.targetLanguage}</>}</strong>
      </div>
      <div className="db-deck-footer"><span>Created</span><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}</time></div>
      <div className="deck-card-actions">
        <Link className="button button-outline" to={`/decks/${deck.id}/edit`} aria-label={`Edit ${deck.name}`}>Edit</Link>
        <button className="button deck-delete-button" type="button" onClick={() => setConfirmingDelete(true)} aria-label={`Delete ${deck.name}`}>Delete</button>
      </div>
    </div>
  </article>{confirmingDelete && <DeleteDeckDialog deck={deck} onCancel={() => setConfirmingDelete(false)}
    onDeleted={() => { setConfirmingDelete(false); onChanged(true) }} onUnavailable={() => { setConfirmingDelete(false); onChanged(false) }} />}</>
}
