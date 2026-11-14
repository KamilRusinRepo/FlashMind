import { useState } from 'react'
import type { StudyCardData } from '../../types/study'

export type StudyAttempt = { cardId: string; userAnswer: string; correct: boolean; usedHint: boolean; skipped: boolean }

export default function StudyCard({ card, result }: { card: StudyCardData; result?: StudyAttempt }) {
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const [showQuestion, setShowQuestion] = useState(false)
  const showBack = !!result && !showQuestion
  const resultClass = result ? result.correct ? ' is-correct' : ' is-wrong' : ''
  const illustration = card.imageUrl && failedImage !== card.imageUrl ? <img className="study-card-image" src={card.imageUrl} alt="Flashcard illustration"
    referrerPolicy="no-referrer" onError={() => setFailedImage(card.imageUrl ?? null)} /> : null

  return <article className={`study-card-scene${result ? ' can-flip' : ''}`} aria-label="Current flashcard"
    onClick={() => { if (result) setShowQuestion(value => !value) }}>
    <div className={`study-card-flipper${showBack ? ' is-flipped' : ''}`}>
      <div className={`study-card study-card-face study-card-front${resultClass}`} aria-hidden={showBack}>
        <p className="study-eyebrow">{card.exampleSentence ? 'FRONT + EXAMPLE' : 'FRONT · QUESTION'}</p>
        <h2>{card.question}</h2><p className="study-card-example">{card.exampleSentence}</p>
        {illustration}
      </div>
      <div className={`study-card study-card-face study-card-back${resultClass}`} aria-hidden={!showBack}>
        {result && <>
          <p className="study-eyebrow">BACK · ANSWER</p><h2>{card.answer}</h2>
          <p className="study-card-example">{card.translatedExample}</p>{illustration}
          <div className="study-feedback" role="status">
            <strong>{result.skipped ? 'Skipped · counted as wrong' : result.correct ? 'Correct!' : 'Not quite. Keep going!'}</strong>
            {!result.skipped && <span>Your answer: {result.userAnswer.trim() || '(empty)'}</span>}
            {result.usedHint && <span>Hint used</span>}
          </div>
        </>}
      </div>
    </div>
    <p className="study-flip-hint">{result
      ? <button className="study-flip-control" type="button" aria-label={`Show ${showBack ? 'question' : 'answer'}`}
          aria-pressed={showBack} onClick={event => { event.stopPropagation(); setShowQuestion(value => !value) }}>
          Click the card to show {showBack ? 'the question' : 'the answer'}
        </button>
      : <span aria-hidden="true">&nbsp;</span>}
    </p>
  </article>
}
