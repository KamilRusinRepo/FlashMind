import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import type { StudyAttempt } from './StudyCard'
import correctIcon from '../../assets/icons/study-summary/correct_back.svg'
import wrongIcon from '../../assets/icons/study-summary/wrong_background.svg'
import xpIcon from '../../assets/icons/study-summary/xp_backgroud.svg'
import dashboardIcon from '../../assets/icons/study-summary/dashboard.svg'
import studyAgainIcon from '../../assets/icons/study-summary/study_again.svg'
import './StudySummary.css'

export default function StudySummary({ deckName, answers, onStudyAgain }: {
  deckName: string; answers: StudyAttempt[]; onStudyAgain: () => void
}) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  const total = answers.length
  const correct = answers.filter(answer => answer.correct).length
  const wrong = total - correct
  const accuracy = total === 0 ? 0 : Math.round(correct / total * 100)
  const wrongPercent = total === 0 ? 0 : Math.round(wrong / total * 100)
  // Visual preview of the PHP scoring rules; no account XP is awarded here.
  const previewXp = answers.reduce((sum, answer) => sum + (answer.correct ? answer.usedHint ? 5 : 10 : 0), 0)

  useEffect(() => {
    const previousTitle = document.title
    document.title = 'Study summary preview | FlashMind'
    window.scrollTo(0, 0)
    headingRef.current?.focus()
    return () => { document.title = previousTitle }
  }, [])

  return <section className="study-summary" aria-labelledby="study-summary-title">
    <span className="study-summary-check" aria-hidden="true">✓</span>
    <p className="study-eyebrow">PRACTICE PREVIEW</p>
    <h1 id="study-summary-title" ref={headingRef} tabIndex={-1}>Session Complete!</h1>
    <p className="study-summary-intro">Great job! Here’s how you did in the practice preview for <strong>“{deckName}”</strong>.</p>

    <div className="study-summary-grid">
      <article className="study-summary-stat is-correct" aria-labelledby="summary-correct-title">
        <h2 id="summary-correct-title">Correct</h2><img className="study-summary-background-icon" src={correctIcon} alt="" />
        <p className="study-summary-value"><strong>{correct}</strong><span>{correct === 1 ? 'card' : 'cards'}</span></p>
        <progress className="study-summary-track" max={100} value={accuracy} aria-label="Correct answers percentage" />
      </article>
      <article className="study-summary-stat is-wrong" aria-labelledby="summary-wrong-title">
        <h2 id="summary-wrong-title">Wrong</h2><img className="study-summary-background-icon" src={wrongIcon} alt="" />
        <p className="study-summary-value"><strong>{wrong}</strong><span>{wrong === 1 ? 'card' : 'cards'}</span></p>
        <progress className="study-summary-track" max={100} value={wrongPercent} aria-label="Wrong answers percentage" />
      </article>
      <article className="study-summary-stat is-xp" aria-labelledby="summary-xp-title">
        <h2 id="summary-xp-title">XP Preview</h2><img className="study-summary-background-icon" src={xpIcon} alt="" />
        <p className="study-summary-value"><strong>+{previewXp}</strong><span>points</span></p>
        <p className="study-summary-xp-note">Example scoring · not awarded</p>
      </article>
    </div>
    <p className="study-summary-accuracy">{total} {total === 1 ? 'card practised' : 'cards practised'}<span aria-hidden="true"> · </span>{accuracy}% accuracy</p>
    <div className="study-summary-actions">
      <Link className="button button-outline" to="/dashboard"><img src={dashboardIcon} alt="" />Dashboard</Link>
      <button className="button button-primary" type="button" onClick={onStudyAgain}><img src={studyAgainIcon} alt="" />Study Again</button>
    </div>
  </section>
}
