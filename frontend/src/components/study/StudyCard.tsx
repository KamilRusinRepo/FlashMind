import { useLanguage } from '../../i18n/LanguageContext'
import { useState } from 'react'
import type { StudyCardData } from '../../types/study'

export type StudyAttempt = { cardId: string; userAnswer: string; correct: boolean; usedHint: boolean; skipped: boolean }

export default function StudyCard({ card, result }: { card: StudyCardData; result?: StudyAttempt }) {
  const { t } = useLanguage()
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const [showQuestion, setShowQuestion] = useState(false)
  const showBack = !!result && !showQuestion
  const resultClass = result ? result.correct ? ' is-correct' : ' is-wrong' : ''
  const illustration = card.imageUrl && failedImage !== card.imageUrl ? <img className="study-card-image" src={card.imageUrl} alt={t("Flashcard illustration")}
    referrerPolicy="no-referrer" onError={() => setFailedImage(card.imageUrl ?? null)} /> : null

  return <article className={`study-card-scene${result ? ' can-flip' : ''}`} aria-label={t("Current flashcard")}
    onClick={() => { if (result) setShowQuestion(value => !value) }}>
    <div className={`study-card-flipper${showBack ? ' is-flipped' : ''}`}>
      <div className={`study-card study-card-face study-card-front${resultClass}`} aria-hidden={showBack}>
        <p className="study-eyebrow">{card.exampleSentence ? t("FRONT + EXAMPLE") : t("FRONT · QUESTION")}</p>
        <h2>{card.question}</h2><p className="study-card-example">{card.exampleSentence}</p>
        {illustration}
      </div>
      <div className={`study-card study-card-face study-card-back${resultClass}`} aria-hidden={!showBack}>
        {result && <>
          <p className="study-eyebrow">{t("BACK · ANSWER")}</p><h2>{card.answer}</h2>
          <p className="study-card-example">{card.translatedExample}</p>{illustration}
          <div className="study-feedback" role="status">
            <strong>{result.skipped ? t("Skipped · counted as wrong") : result.correct ? t("Correct!") : t("Not quite. Keep going!")}</strong>
            {!result.skipped && <span>{t("Your answer:")} {result.userAnswer.trim() || t("(empty)")}</span>}
            {result.usedHint && <span>{t("Hint used")}</span>}
          </div>
        </>}
      </div>
    </div>
    <p className="study-flip-hint">{result
      ? <button className="study-flip-control" type="button" aria-label={t("Show {0}", [t(showBack ? 'question' : 'answer')])}
          aria-pressed={showBack} onClick={event => { event.stopPropagation(); setShowQuestion(value => !value) }}>{t("Click the card to show")} {showBack ? t("the question") : t("the answer")}
        </button>
      : <span aria-hidden="true">&nbsp;</span>}
    </p>
  </article>
}
