import type { PreviewFlashcard } from '../../data/flashcardPreview'

export default function FlashcardPreview({ card, index }: { card: PreviewFlashcard; index: number }) {
  return <article className="flashcard-preview">
    <div className="flashcard-preview-top"><span className="flashcard-preview-number">{String(index + 1).padStart(2, '0')}</span><span>EXAMPLE CARD</span></div>
    <div className="flashcard-preview-grid">
      <div className="flashcard-preview-side"><p className="flashcard-side-label">FRONT · QUESTION</p><h3>{card.question}</h3>
        {card.exampleSentence && <p className="flashcard-example">{card.exampleSentence}</p>}
      </div>
      <div className="flashcard-preview-side"><p className="flashcard-side-label">BACK · ANSWER</p><p className="flashcard-answer">{card.answer}</p>
        {card.translatedExample && <p className="flashcard-example">{card.translatedExample}</p>}
      </div>
    </div>
  </article>
}
