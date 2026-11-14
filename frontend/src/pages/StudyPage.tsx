import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { DeckApiError, getOwnedDeck, type OwnedDeck } from '../api/decks'
import { getStudySession, StudyApiError, type StudySessionData } from '../api/study'
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
  return <StudySession session={state.data} />
}

function StudySession({ session }: { session: StudySessionData }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const { deck } = session
  const card = session.cards[0]
  useEffect(() => { document.title = `Study: ${deck.name} | FlashMind`; inputRef.current?.focus() }, [deck.name])

  return <div className="study-layout">
    <StudySidebar deck={deck} correct={0} wrong={0} />
    <section className="study-content" aria-label="Study practice">
      <div className="study-progress-label"><span>Session Progress</span><strong>1 of {session.totalCards} cards</strong></div>
      <progress className="study-progress study-session-progress" max={session.totalCards} value={0} aria-label="Answered flashcards" />
      <StudyCard key={card.id} card={{ id: String(card.id), question: card.question, exampleSentence: card.exampleSentence ?? undefined, imageUrl: card.imageUrl ?? undefined }} />
      <form className="study-answer-form" onSubmit={event => event.preventDefault()}>
        <div className="study-answer-field"><label className="study-input-label" htmlFor="study-answer">{deck.deckType === 'LANGUAGE' ? 'Your translation' : 'Your answer'}</label>
          <input ref={inputRef} id="study-answer" type="text" maxLength={2000} autoComplete="off" spellCheck={false}
            placeholder={deck.deckType === 'LANGUAGE' ? 'Type translation…' : 'Type your answer…'} aria-describedby="study-answer-help" /></div>
        <button className="button button-primary study-submit" type="submit" disabled title="Answer checking is coming next"><span>Submit</span><img src={submitIcon} alt="" /></button>
      </form>
      <div className="study-actions"><button className="study-text-action" type="button" disabled title="Hints are coming next"><img src={hintIcon} alt="" />Hint</button>
        <button className="study-text-action" type="button" disabled title="Saving answers is coming next"><img src={skipIcon} alt="" />Skip</button>
        <span id="study-answer-help">Session started. Answer checking will be connected in the next step.</span></div>
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
