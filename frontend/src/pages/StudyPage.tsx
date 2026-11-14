import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { DeckApiError, getOwnedDeck, type OwnedDeck } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import StudySidebar from '../components/study/StudySidebar'
import StudyCard, { type StudyAttempt } from '../components/study/StudyCard'
import { generalStudyCards, languageStudyCards, matchesStudyAnswer, studyAnswerOptions } from '../data/studyPreview'
import hintIcon from '../assets/icons/study/hint.svg'
import skipIcon from '../assets/icons/study/skip.svg'
import submitIcon from '../assets/icons/study/submit.svg'
import './Study.css'

type LoadState = { status: 'loading' } | { status: 'ready'; deck: OwnedDeck } | { status: 'error'; message: string; code: number }

export default function StudyPage() {
  const { deckId } = useParams()
  const { user } = useAuth()
  const id = Number(deckId)
  const valid = /^\d+$/.test(deckId ?? '') && Number.isSafeInteger(id) && id > 0
  return <main className="study-shell">
    {valid ? <StudyLoader key={`${user?.id}:${id}`} deckId={id} /> : <StudyUnavailable />}
  </main>
}

function StudyLoader({ deckId }: { deckId: number }) {
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    document.title = 'Study preview | FlashMind'
    window.scrollTo(0, 0)
    const controller = new AbortController()
    getOwnedDeck(deckId, controller.signal).then(deck => {
      if (!controller.signal.aborted) setState({ status: 'ready', deck })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load this deck.', code: cause instanceof DeckApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deckId, attempt, refresh])
  if (state.status === 'loading') return <p className="study-load-message" role="status">Loading your deck…</p>
  if (state.status === 'error') return state.code === 404 ? <StudyUnavailable /> : <div className="study-load-message">
    <p className="form-notice form-error" role="alert">{state.message}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Try again</button>
    <Link className="button button-outline" to={`/decks/${deckId}`}>Back to deck</Link>
  </div>
  return <StudySession deck={state.deck} />
}

function StudySession({ deck }: { deck: OwnedDeck }) {
  const cards = deck.deckType === 'LANGUAGE' ? languageStudyCards : generalStudyCards
  const [index, setIndex] = useState(0)
  const [guess, setGuess] = useState('')
  const [usedHint, setUsedHint] = useState(false)
  const [answers, setAnswers] = useState<StudyAttempt[]>([])
  const [finished, setFinished] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const submitRef = useRef<HTMLButtonElement>(null)
  const completeRef = useRef<HTMLHeadingElement>(null)
  const answeredRef = useRef(false)
  const card = cards[index]
  const result = answers[index]
  const correct = answers.filter(answer => answer.correct).length

  useEffect(() => {
    if (finished) completeRef.current?.focus()
    else if (result) submitRef.current?.focus()
    else inputRef.current?.focus()
  }, [index, result, finished])

  function checkAnswer(skipped = false) {
    if (answeredRef.current || finished) return
    answeredRef.current = true
    setAnswers(current => [...current, { cardId: card.id, userAnswer: skipped ? '' : guess,
      correct: !skipped && matchesStudyAnswer(guess, card.answer), usedHint, skipped }])
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (finished || (result && !answeredRef.current)) return
    if (!result) { checkAnswer(); return }
    if (index === cards.length - 1) { setFinished(true); return }
    setIndex(value => value + 1)
    setGuess('')
    setUsedHint(false)
    answeredRef.current = false
  }

  function hint() {
    if (answeredRef.current) return
    const letters = Array.from(studyAnswerOptions(card.answer)[0] ?? '')
    setGuess(letters.slice(0, Math.max(1, Math.ceil(letters.length / 3))).join(''))
    setUsedHint(true)
    inputRef.current?.focus()
  }

  function restart() {
    setIndex(0); setGuess(''); setUsedHint(false); setAnswers([]); setFinished(false); answeredRef.current = false
  }

  return <>
    <div className="study-layout">
      <StudySidebar deck={deck} correct={correct} wrong={answers.length - correct} />
      <section className="study-content" aria-label="Study practice">
        {finished ? <div className="study-complete"><p className="study-eyebrow">PREVIEW COMPLETE</p>
          <h2 ref={completeRef} tabIndex={-1}>You’ve reached the end!</h2><p>Your practice results have not been saved. You can try the preview again or return to your deck.</p>
          <div className="study-complete-actions"><button className="button button-primary" type="button" onClick={restart}>Try preview again</button>
            <Link className="button button-outline" to={`/decks/${deck.id}`}>Back to deck</Link></div>
        </div> : <>
          <div className="study-progress-label"><span>Session Progress</span><strong>{index + 1} of {cards.length} cards</strong></div>
          <progress className="study-progress study-session-progress" max={cards.length} value={answers.length} aria-label="Answered flashcards" />
          <StudyCard key={card.id} card={card} result={result} />
          <form className="study-answer-form" onSubmit={handleSubmit}>
            <div className="study-answer-field"><label className="study-input-label" htmlFor="study-answer">{deck.deckType === 'LANGUAGE' ? 'Your translation' : 'Your answer'}</label>
              <input ref={inputRef} id="study-answer" type="text" value={guess} maxLength={2000} disabled={!!result} autoComplete="off" spellCheck={false}
                placeholder={deck.deckType === 'LANGUAGE' ? 'Type translation…' : 'Type your answer…'}
                aria-describedby="study-answer-help" onChange={event => setGuess(event.target.value)} /></div>
            <button ref={submitRef} className="button button-primary study-submit" type="submit"><span>{result ? index === cards.length - 1 ? 'Finish session' : 'Next' : 'Submit'}</span><img src={submitIcon} alt="" /></button>
          </form>
          <div className="study-actions"><button className="study-text-action" type="button" disabled={!!result} onClick={hint}><img src={hintIcon} alt="" />Hint</button>
            <button className="study-text-action" type="button" disabled={!!result} onClick={() => checkAnswer(true)}><img src={skipIcon} alt="" />Skip</button>
            <span id="study-answer-help">{result ? 'Press Enter to continue' : 'Press Enter to submit'}{usedHint && !result && ' · Hint used'}</span></div>
        </>}
      </section>
    </div>
  </>
}

function StudyUnavailable() {
  return <div className="study-load-message"><h1>Deck not found</h1><p>This deck is unavailable or does not belong to your account.</p><Link className="button button-primary" to="/decks">Go to My Decks</Link></div>
}
