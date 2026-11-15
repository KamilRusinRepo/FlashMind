import { useLanguage } from '../../i18n/LanguageContext'
import { isStatisticsOverview } from '../../api/statistics'
import { useStatisticsResource } from '../statistics/useStatisticsResource'

export default function DashboardStats() {
  const { t, count, locale } = useLanguage()
  const { state, retry } = useStatisticsResource('/api/statistics/me', isStatisticsOverview)
  if (state.status === 'loading') return <div className="db-metrics" aria-label={t('Learning statistics')} aria-busy="true">
    <p className="db-metric db-metrics-status" role="status">{t('Loading statistics…')}</p>
  </div>
  if (state.status === 'error') return <div className="db-metrics" aria-label={t('Learning statistics')}>
    <div className="db-metric db-metrics-status"><p role="alert">{t(state.message)}</p><button className="db-text-action" type="button" onClick={retry}>{t('Try again')}</button></div>
  </div>
  const { streak, dailyGoal, level, totalXp, currentLevelXp, nextLevelXp } = state.data.profile
  const studiedToday = state.data.periods.day.studied
  const number = new Intl.NumberFormat(locale)

  return <div className="db-metrics" aria-label={t('Learning statistics')}>
    <div className="db-metric db-streak"><span className="db-metric-symbol" aria-hidden="true">🔥</span>
      <div><strong>{count(streak, 'day')}</strong><span>{t("Study streak")}</span></div>
    </div>
    <div className="db-metric"><span className="db-goal-symbol" aria-hidden="true">✓</span>
      <div><strong>{number.format(studiedToday)}/{number.format(dailyGoal)} {t("cards")}</strong><span>{t("Daily goal")}</span></div>
    </div>
    <div className="db-metric db-level">
      <div className="db-progress-label"><span>{t("LEVEL")} {number.format(level)}</span><strong>{number.format(currentLevelXp)}/{number.format(nextLevelXp)} {t("XP")}</strong></div>
      <progress className="db-progress" value={currentLevelXp} max={nextLevelXp} aria-label={t("Level {0}: {1} of {2} XP", [number.format(level), number.format(currentLevelXp), number.format(nextLevelXp)])} />
      <span className="db-total-xp">{t('Total XP')}: {number.format(totalXp)}</span>
    </div>
  </div>
}
