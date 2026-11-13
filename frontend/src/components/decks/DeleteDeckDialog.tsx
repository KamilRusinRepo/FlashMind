import { useEffect, useId, useRef } from 'react'
import type { OwnedDeck } from '../../api/decks'

export default function DeleteDeckDialog({ deck, onCancel }: { deck: OwnedDeck; onCancel: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => { if (dialog?.open) dialog.close() }
  }, [])

  return <dialog ref={dialogRef} className="deck-delete-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); onCancel() }}>
    <h2 id={titleId}>Delete deck?</h2>
    <p id={descriptionId}>You’re about to delete <strong>{deck.name}</strong>. This action will be permanent.</p>
    <p className="form-notice">Preview: deleting decks is coming soon. No deck will be removed yet.</p>
    <div className="deck-form-actions">
      <button className="button button-outline" type="button" onClick={onCancel}>Cancel</button>
      <button className="button deck-delete-button" type="button" disabled title="Deleting decks is coming soon">Delete Deck</button>
    </div>
  </dialog>
}
