import type { PreviewFlashcard } from '../../data/flashcardPreview'
import FlashcardImage from './FlashcardImage'

export default function FlashcardPreview({ card, index, onEdit, onDelete }: {
  card: PreviewFlashcard; index: number; onEdit: () => void; onDelete: () => void
}) {
  return <article className="flashcard-preview">
    <div className="flashcard-preview-top"><span className="flashcard-preview-number">{String(index + 1).padStart(2, '0')}</span><span>PREVIEW CARD</span>
      <div className="flashcard-preview-actions">
        <button className="button button-outline" type="button" onClick={onEdit} aria-label={`Edit flashcard ${index + 1}`}>Edit</button>
        <button className="button deck-delete-button" type="button" onClick={onDelete} aria-label={`Delete flashcard ${index + 1}`}>Delete</button>
      </div>
    </div>
    <div className="flashcard-preview-grid">
      <div className="flashcard-preview-side"><p className="flashcard-side-label">FRONT · QUESTION</p><h3>{card.question}</h3>
        {card.exampleSentence && <p className="flashcard-example">{card.exampleSentence}</p>}
      </div>
      <div className={`flashcard-preview-side${card.imageUrl ? ' flashcard-answer-with-image' : ''}`}><p className="flashcard-side-label">BACK · ANSWER</p>
        <p className="flashcard-answer">{card.answer}</p>
        {card.translatedExample && <p className="flashcard-example">{card.translatedExample}</p>}
        <FlashcardImage url={card.imageUrl} />
      </div>
    </div>
  </article>
}
