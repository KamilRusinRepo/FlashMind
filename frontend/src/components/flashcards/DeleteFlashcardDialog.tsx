import type { PreviewFlashcard } from '../../data/flashcardPreview'
import FlashcardDialog from './FlashcardDialog'

export default function DeleteFlashcardDialog({ card, onCancel, onDelete, previewOnly = false }: {
  card: PreviewFlashcard; onCancel: () => void; onDelete: () => void; previewOnly?: boolean
}) {
  return <FlashcardDialog title="Delete flashcard?" onCancel={onCancel}>
    <p className="flashcard-delete-question">Delete <strong>{card.question}</strong>?</p>
    <div className="flashcard-dialog-actions">
      <button className="button button-outline" type="button" onClick={onCancel}>Cancel</button>
      <button className="button deck-delete-button" type="button" disabled={previewOnly} onClick={onDelete}>{previewOnly ? 'Deletion coming soon' : 'Delete from preview'}</button>
    </div>
  </FlashcardDialog>
}
