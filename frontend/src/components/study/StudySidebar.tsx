import { useLanguage } from '../../i18n/LanguageContext'
import { Link } from 'react-router'
import type { OwnedDeck } from '../../api/decks'
import exitIcon from '../../assets/icons/study/exit.svg'
import languageIcon from '../../assets/icons/study/lang.svg'
import activeIcon from '../../assets/icons/study/active_session.svg'
import leaderboardIcon from '../../assets/icons/study/leaderboard.svg'
import goalIcon from '../../assets/icons/study/daily_goal.svg'
import tipIcon from '../../assets/icons/study/study_tip.svg'

export default function StudySidebar({ deck, correct, wrong }: { deck: OwnedDeck; correct: number; wrong: number }) {
  const { t } = useLanguage()
  const accuracy = correct + wrong === 0 ? 0 : Math.round(correct / (correct + wrong) * 100)
  return <aside className="study-sidebar" aria-label={t("Study session information")}>
    <Link className="study-exit" to={`/decks/${deck.id}`} aria-label={t("Exit session and return to deck")} title={t("Exit to deck")}><img src={exitIcon} alt="" /></Link>
    <section className="study-panel">
      <div className="study-panel-header"><span className="study-deck-icon"><img src={languageIcon} alt="" /></span>
        <div><h1>{deck.name}</h1><p className="study-deck-description">{deck.description || t("Make a little room for learning.")}</p></div></div>
      <div className="study-panel-nav" aria-label={t("Study tools")}>
        <div className="study-active-label"><img src={activeIcon} alt="" />{t("Active Session")}</div>
        <button className="study-nav-placeholder" type="button" disabled title={t("Leaderboard is coming soon")}><img src={leaderboardIcon} alt="" />{t("Leaderboard")}</button>
        <button className="study-nav-placeholder" type="button" disabled title={t("Daily Goal is coming soon")}><img src={goalIcon} alt="" />{t("Daily Goal")}</button>
      </div>
    </section>
    <section className="study-panel" aria-labelledby="study-stats-title">
      <h2 id="study-stats-title">{t("Current Session Stats")}</h2>
      <div className="study-stat-grid"><div className="study-stat is-correct"><strong>{correct}</strong><span>{t("Correct")}</span></div>
        <div className="study-stat is-wrong"><strong>{wrong}</strong><span>{t("Wrong")}</span></div></div>
      <div className="study-accuracy-label"><span>{t("Accuracy")}</span><strong>{accuracy}%</strong></div>
      <progress className="study-progress" max={100} value={accuracy} aria-label={t("Answer accuracy")} />
    </section>
    <section className="study-tip"><h2><img src={tipIcon} alt="" />{t("Study Tip")}</h2><p>{deck.deckType === 'LANGUAGE'
      ? t("Say the word out loud as you type it. A small habit can help a new word stick.")
      : t("Try recalling the answer before using a hint. Retrieving it helps you remember.")}</p></section>
  </aside>
}
