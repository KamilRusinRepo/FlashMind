import { useLanguage } from '../../i18n/LanguageContext'
import { dashboardStats } from '../../data/dashboard'

export default function DashboardStats() {
  const { t, count } = useLanguage()
  const { streak, studiedToday, dailyGoal, level, xp, nextLevelXp } = dashboardStats

  return <div className="db-metrics" aria-label={t("Sample learning statistics")}>
    <div className="db-metric db-streak"><span className="db-metric-symbol" aria-hidden="true">🔥</span>
      <div><strong>{count(streak, 'day')}</strong><span>{t("Study streak")}</span></div>
    </div>
    <div className="db-metric"><span className="db-goal-symbol" aria-hidden="true">✓</span>
      <div><strong>{studiedToday}/{dailyGoal} {t("cards")}</strong><span>{t("Daily goal")}</span></div>
    </div>
    <div className="db-metric db-level">
      <div className="db-progress-label"><span>{t("LEVEL")} {level}</span><strong>{xp}/{nextLevelXp} {t("XP")}</strong></div>
      <progress className="db-progress" value={xp} max={nextLevelXp} aria-label={t("Level {0}: {1} of {2} XP", [level, xp, nextLevelXp])} />
    </div>
  </div>
}
