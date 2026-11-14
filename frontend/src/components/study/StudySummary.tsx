import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import type { StudySummaryData } from '../../api/study'
import StudyStartButton from './StudyStartButton'
import correctIcon from '../../assets/icons/study-summary/correct_back.svg'
import wrongIcon from '../../assets/icons/study-summary/wrong_background.svg'
import xpIcon from '../../assets/icons/study-summary/xp_backgroud.svg'
import dashboardIcon from '../../assets/icons/study-summary/dashboard.svg'
import studyAgainIcon from '../../assets/icons/study-summary/study_again.svg'
import './StudySummary.css'

export default function StudySummary({ summary }: { summary: StudySummaryData }) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  const { deckName, totalCards: total, correctCards: correct, wrongCards: wrong, accuracy, xpEarned, durationSeconds } = summary
  const wrongPercent = 100 - accuracy
  const duration = `${Math.floor(durationSeconds / 60)}m ${durationSeconds % 60}s`

  useEffect(() => {
    const previousTitle = document.title
    document.title = 'Study summary | FlashMind'
    window.scrollTo(0, 0)
    headingRef.current?.focus()
    return () => { document.title = previousTitle }
  }, [])

  return <section className="study-summary" aria-labelledby="study-summary-title">
    <span className="study-summary-check" aria-hidden="true">✓</span>
    <p className="study-eyebrow">SESSION RESULTS</p>
    <h1 id="study-summary-title" ref={headingRef} tabIndex={-1}>Session Complete!</h1>
    <p className="study-summary-intro">Great job! Here’s how you did in <strong>“{deckName}”</strong>.</p>

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
        <h2 id="summary-xp-title">Session XP</h2><img className="study-summary-background-icon" src={xpIcon} alt="" />
        <p className="study-summary-value"><strong>+{xpEarned}</strong><span>points</span></p>
        <p className="study-summary-xp-note">10 per correct answer · 5 with a hint</p>
      </article>
    </div>
    <p className="study-summary-accuracy">{total} {total === 1 ? 'card practised' : 'cards practised'}<span aria-hidden="true"> · </span>{accuracy}% accuracy<span aria-hidden="true"> · </span>{duration}</p>
    <div className="study-summary-actions">
      <Link className="button button-outline" to="/dashboard"><img src={dashboardIcon} alt="" />Dashboard</Link>
      <StudyStartButton deckId={summary.deckId} label="Study Again" icon={studyAgainIcon} />
    </div>
  </section>
}
