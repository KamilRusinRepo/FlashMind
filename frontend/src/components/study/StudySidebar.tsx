import { Link } from 'react-router'
import type { OwnedDeck } from '../../api/decks'
import exitIcon from '../../assets/icons/study/exit.svg'
import languageIcon from '../../assets/icons/study/lang.svg'
import activeIcon from '../../assets/icons/study/active_session.svg'
import leaderboardIcon from '../../assets/icons/study/leaderboard.svg'
import goalIcon from '../../assets/icons/study/daily_goal.svg'
import tipIcon from '../../assets/icons/study/study_tip.svg'

export default function StudySidebar({ deck, correct, wrong }: { deck: OwnedDeck; correct: number; wrong: number }) {
  const accuracy = correct + wrong === 0 ? 0 : Math.round(correct / (correct + wrong) * 100)
  return <aside className="study-sidebar" aria-label="Study session information">
    <Link className="study-exit" to={`/decks/${deck.id}`} aria-label="Exit session and return to deck" title="Exit to deck"><img src={exitIcon} alt="" /></Link>
    <section className="study-panel">
      <div className="study-panel-header"><span className="study-deck-icon"><img src={languageIcon} alt="" /></span>
        <div><h1>{deck.name}</h1><p className="study-deck-description">{deck.description || 'Make a little room for learning.'}</p></div></div>
      <div className="study-panel-nav" aria-label="Study tools">
        <div className="study-active-label"><img src={activeIcon} alt="" />Active Session</div>
        <button className="study-nav-placeholder" type="button" disabled title="Leaderboard is coming soon"><img src={leaderboardIcon} alt="" />Leaderboard</button>
        <button className="study-nav-placeholder" type="button" disabled title="Daily Goal is coming soon"><img src={goalIcon} alt="" />Daily Goal</button>
      </div>
    </section>
    <section className="study-panel" aria-labelledby="study-stats-title">
      <h2 id="study-stats-title">Current Session Stats</h2>
      <div className="study-stat-grid"><div className="study-stat is-correct"><strong>{correct}</strong><span>Correct</span></div>
        <div className="study-stat is-wrong"><strong>{wrong}</strong><span>Wrong</span></div></div>
      <div className="study-accuracy-label"><span>Accuracy</span><strong>{accuracy}%</strong></div>
      <progress className="study-progress" max={100} value={accuracy} aria-label="Answer accuracy" />
    </section>
    <section className="study-tip"><h2><img src={tipIcon} alt="" />Study Tip</h2><p>{deck.deckType === 'LANGUAGE'
      ? 'Say the word out loud as you type it. A small habit can help a new word stick.'
      : 'Try recalling the answer before using a hint. Retrieving it helps you remember.'}</p></section>
  </aside>
}
