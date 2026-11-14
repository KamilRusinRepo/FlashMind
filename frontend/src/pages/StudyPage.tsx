import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { DeckApiError, getOwnedDeck, type OwnedDeck } from '../api/decks'
import { getStudySession, requestStudyHint, submitStudyAnswer, StudyApiError, type StudySessionData } from '../api/study'
import { useAuth } from '../auth/AuthContext'
import StudySidebar from '../components/study/StudySidebar'
import StudyCard from '../components/study/StudyCard'
import StudyStartButton from '../components/study/StudyStartButton'
import hintIcon from '../assets/icons/study/hint.svg'
import skipIcon from '../assets/icons/study/skip.svg'
import submitIcon from '../assets/icons/study/submit.svg'
import './Study.css'

type LoadState<T> = { status: 'loading' } | { status: 'ready'; data: T } | { status: 'error'; message: string; code: number }
const validId = (value: string | undefined) => /^\d+$/.test(value ?? '') && Number.isSafeInteger(Number(value)) && Number(value) > 0

export default function StudyPage() {
  const { deckId, sessionId } = useParams()
  const { user } = useAuth()
  return <main className="study-shell">
    {!validId(deckId) || (sessionId !== undefined && !validId(sessionId)) ? <StudyUnavailable />
      : sessionId === undefined ? <StudyLanding key={`${user?.id}:${deckId}`} deckId={Number(deckId)} />
        : <StudyLoader key={`${user?.id}:${deckId}:${sessionId}`} deckId={Number(deckId)} sessionId={Number(sessionId)} />}
  </main>
}

function StudyLanding({ deckId }: { deckId: number }) {
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState<OwnedDeck>>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    document.title = 'Start study | FlashMind'
    window.scrollTo(0, 0)
    const controller = new AbortController()
    getOwnedDeck(deckId, controller.signal).then(deck => {
      if (!controller.signal.aborted) setState({ status: 'ready', data: deck })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load this deck.', code: cause instanceof DeckApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deckId, attempt, refresh])
  if (state.status === 'loading') return <p className="study-load-message" role="status">Loading your deck…</p>
  if (state.status === 'error') return <StudyLoadError state={state} deckId={deckId} retry={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }} />
  return <section className="study-load-message study-entry"><h1>{state.data.name}</h1>
    <p>Start a session with up to 10 flashcards. Due reviews are selected first, followed by new cards.</p>
    <StudyStartButton deckId={deckId} /><Link className="button button-outline" to={`/decks/${deckId}`}>Back to deck</Link>
  </section>
}

function StudyLoader({ deckId, sessionId }: { deckId: number; sessionId: number }) {
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState<StudySessionData>>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    document.title = 'Study session | FlashMind'
    window.scrollTo(0, 0)
    const controller = new AbortController()
    getStudySession(sessionId, controller.signal).then(session => {
      if (controller.signal.aborted) return
      if (session.deck.id !== deckId) { setState({ status: 'error', code: 404, message: 'Study session not found.' }); return }
      setState({ status: 'ready', data: session })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof StudyApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof StudyApiError ? cause.message : 'Unable to load this study session.', code: cause instanceof StudyApiError ? cause.status : 0 })
    })
    return () => controller.abort()
  }, [deckId, sessionId, attempt, refresh])
  if (state.status === 'loading') return <p className="study-load-message" role="status">Loading your study session…</p>
  if (state.status === 'error') return <StudyLoadError state={state} deckId={deckId} retry={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }} />
  if (state.data.status !== 'ACTIVE') return <section className="study-load-message study-entry"><h1>This session has ended</h1>
    <StudyStartButton deckId={deckId} /><Link className="button button-outline" to={`/decks/${deckId}`}>Back to deck</Link></section>
  return <StudySession session={state.data} onReload={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }} />
}

function StudySession({ session, onReload }: { session: StudySessionData; onReload: () => void }) {
  const { refresh } = useAuth()
  const [cards, setCards] = useState(session.cards)
  const [index, setIndex] = useState(() => {
    const pending = session.cards.findIndex(card => card.result === null)
    return pending === -1 ? session.cards.length - 1 : pending
  })
  const card = cards[index]
  const result = card.result
  const [guess, setGuess] = useState(result?.userAnswer ?? card.hint ?? '')
  const [sending, setSending] = useState<'answer' | 'hint' | 'skip' | null>(null)
  const [error, setError] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const submitRef = useRef<HTMLButtonElement>(null)
  const savedRef = useRef<HTMLSpanElement>(null)
  const busy = useRef(false)
  const mounted = useRef(true)
  const { deck } = session
  const correct = cards.filter(card => card.result?.correct === true).length
  const answered = cards.filter(card => card.result !== null).length
  const allSaved = answered === cards.length
  const last = index === cards.length - 1

  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    document.title = `Study: ${deck.name} | FlashMind`
    if (sending) return
    if (result) {
      if (last) savedRef.current?.focus()
      else submitRef.current?.focus()
    } else inputRef.current?.focus()
  }, [deck.name, card.id, result, last, sending])

  async function act(action: 'answer' | 'hint' | 'skip') {
    if (busy.current || result) return
    busy.current = true
    setSending(action)
    setError('')
    try {
      const updated = action === 'hint' ? await requestStudyHint(session.id, card.id)
        : await submitStudyAnswer(session.id, card.id, action === 'skip' ? '' : guess, action === 'skip')
      if (!mounted.current) return
      if (updated.position !== card.position) throw new StudyApiError('Unexpected flashcard result. Refresh the session.')
      setCards(current => current.map(item => item.id === updated.id ? updated : item))
      setGuess(updated.result?.userAnswer ?? updated.hint ?? '')
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof StudyApiError && cause.status === 401) { refresh(); return }
      setError(cause instanceof StudyApiError ? cause.message : 'Unable to save this action. Please try again.')
    } finally {
      busy.current = false
      if (mounted.current) setSending(null)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current) return
    if (!result) { void act('answer'); return }
    if (last) return // Completing the session is the next implementation step.
    const next = cards[index + 1]
    setIndex(value => value + 1)
    setGuess(next.result?.userAnswer ?? next.hint ?? '')
    setError('')
  }

  return <div className="study-layout">
    <StudySidebar deck={deck} correct={correct} wrong={answered - correct} />
    <section className="study-content" aria-label="Study practice">
      <div className="study-progress-label"><span>Session Progress</span><strong>{index + 1} of {session.totalCards} cards</strong></div>
      <progress className="study-progress study-session-progress" max={session.totalCards} value={answered} aria-label="Answered flashcards" />
      <StudyCard key={card.id} card={{ id: String(card.id), question: card.question, answer: result?.answer,
        exampleSentence: card.exampleSentence ?? undefined, translatedExample: result?.translatedExample ?? undefined, imageUrl: card.imageUrl ?? undefined }}
        result={result ? { cardId: String(card.id), userAnswer: result.userAnswer, correct: result.correct, usedHint: result.usedHint, skipped: result.skipped } : undefined} />
      {error && <div className="form-notice form-error study-action-error" role="alert"><p>{error}</p>
        <button className="button button-outline" type="button" disabled={!!sending} onClick={onReload}>Refresh session</button></div>}
      <form className="study-answer-form" onSubmit={handleSubmit} aria-busy={!!sending}>
        <div className="study-answer-field"><label className="study-input-label" htmlFor="study-answer">{deck.deckType === 'LANGUAGE' ? 'Your translation' : 'Your answer'}</label>
          <input ref={inputRef} id="study-answer" type="text" value={guess} onChange={event => setGuess(event.target.value)} maxLength={2000}
            disabled={!!result || !!sending} autoComplete="off" spellCheck={false}
            placeholder={deck.deckType === 'LANGUAGE' ? 'Type translation…' : 'Type your answer…'} aria-describedby="study-answer-help" /></div>
        <button ref={submitRef} className="button button-primary study-submit" type="submit" disabled={!!sending || (!!result && last)}
          onClick={event => { if (event.detail > 1) event.preventDefault() }}
          title={result && last ? 'Session completion will be connected next' : undefined}>
          <span>{sending === 'answer' ? 'Checking…' : result ? last ? 'Finish session' : 'Next' : 'Submit'}</span><img src={submitIcon} alt="" /></button>
      </form>
      <div className="study-actions"><button className="study-text-action" type="button" disabled={!!sending || !!result} onClick={() => void act('hint')}>
          <img src={hintIcon} alt="" />{sending === 'hint' ? 'Loading…' : 'Hint'}</button>
        <button className="study-text-action" type="button" disabled={!!sending || !!result} onClick={() => void act('skip')}>
          <img src={skipIcon} alt="" />{sending === 'skip' ? 'Saving…' : 'Skip'}</button>
        <span id="study-answer-help" ref={savedRef} tabIndex={allSaved ? -1 : undefined}>{allSaved
          ? 'All answers saved. Session completion will be connected next.'
          : result ? 'Press Enter to continue' : card.usedHint ? 'Hint used · Press Enter to submit' : 'Press Enter to submit'}</span></div>
    </section>
  </div>
}
function StudyLoadError({ state, deckId, retry }: { state: { message: string; code: number }; deckId: number; retry: () => void }) {
  if (state.code === 404) return <StudyUnavailable />
  return <div className="study-load-message"><p className="form-notice form-error" role="alert">{state.message}</p>
    <button className="button button-outline" type="button" onClick={retry}>Try again</button>
    <Link className="button button-outline" to={`/decks/${deckId}`}>Back to deck</Link></div>
}

function StudyUnavailable() {
  return <div className="study-load-message"><h1>Study session not found</h1><p>This deck or session is unavailable or does not belong to your account.</p>
    <Link className="button button-primary" to="/decks">Go to My Decks</Link></div>
}
