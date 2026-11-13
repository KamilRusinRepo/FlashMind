import type { PreviewFlashcard } from '../../data/flashcardPreview'
import FlashcardDialog from './FlashcardDialog'

export default function DeleteFlashcardDialog({ card, onCancel, onDelete }: {
  card: PreviewFlashcard; onCancel: () => void; onDelete: () => void
}) {
  return <FlashcardDialog title="Delete flashcard?" onCancel={onCancel}>
    <p className="flashcard-delete-question">Remove <strong>{card.question}</strong> from this preview?</p>
    <div className="flashcard-dialog-actions">
      <button className="button button-outline" type="button" onClick={onCancel}>Cancel</button>
      <button className="button deck-delete-button" type="button" onClick={onDelete}>Delete from preview</button>
    </div>
  </FlashcardDialog>
}
