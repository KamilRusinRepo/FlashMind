import { dashboardStats } from '../../data/dashboard'

export default function DashboardStats() {
  const { streak, studiedToday, dailyGoal, level, xp, nextLevelXp } = dashboardStats

  return <div className="db-metrics" aria-label="Sample learning statistics">
    <div className="db-metric db-streak"><span className="db-metric-symbol" aria-hidden="true">🔥</span>
      <div><strong>{streak} days</strong><span>Study streak</span></div>
    </div>
    <div className="db-metric"><span className="db-goal-symbol" aria-hidden="true">✓</span>
      <div><strong>{studiedToday}/{dailyGoal} cards</strong><span>Daily goal</span></div>
    </div>
    <div className="db-metric db-level">
      <div className="db-progress-label"><span>LEVEL {level}</span><strong>{xp}/{nextLevelXp} XP</strong></div>
      <progress className="db-progress" value={xp} max={nextLevelXp} aria-label={`Level ${level}: ${xp} of ${nextLevelXp} XP`} />
    </div>
  </div>
}
