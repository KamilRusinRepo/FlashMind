import { useLanguage } from '../../i18n/LanguageContext'
import type { PreviewFlashcard } from '../../data/flashcardPreview'
import FlashcardImage from './FlashcardImage'

export default function FlashcardPreview({ card, index, onEdit, onDelete, preview = true }: {
  card: PreviewFlashcard; index: number; onEdit?: () => void; onDelete?: () => void; preview?: boolean
}) {
  const { t } = useLanguage()
  return <article className="flashcard-preview">
    <div className="flashcard-preview-top"><span className="flashcard-preview-number">{String(index + 1).padStart(2, '0')}</span><span>{preview ? t("PREVIEW CARD") : t("FLASHCARD")}</span>
      {(onEdit || onDelete) && <div className="flashcard-preview-actions">
        {onEdit && <button className="button button-outline" type="button" onClick={onEdit} aria-label={t("Edit flashcard {0}", [index + 1])}>{t("Edit")}</button>}
        {onDelete && <button className="button deck-delete-button" type="button" onClick={onDelete} aria-label={t("Delete flashcard {0}", [index + 1])}>{t("Delete")}</button>}
      </div>}
    </div>
    <div className="flashcard-preview-grid">
      <div className="flashcard-preview-side"><p className="flashcard-side-label">{t("FRONT · QUESTION")}</p><h3>{card.question}</h3>
        {card.exampleSentence && <p className="flashcard-example">{card.exampleSentence}</p>}
      </div>
      <div className={`flashcard-preview-side${card.imageUrl ? ' flashcard-answer-with-image' : ''}`}><p className="flashcard-side-label">{t("BACK · ANSWER")}</p>
        <p className="flashcard-answer">{card.answer}</p>
        {card.translatedExample && <p className="flashcard-example">{card.translatedExample}</p>}
        <FlashcardImage url={card.imageUrl} />
      </div>
    </div>
  </article>
}
