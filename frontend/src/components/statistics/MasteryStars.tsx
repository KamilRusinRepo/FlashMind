import { useLanguage } from '../../i18n/LanguageContext'
import type { MasteryLevel } from '../../data/mastery'

export default function MasteryStars({ level }: { level: MasteryLevel }) {
  const { t } = useLanguage()
  return <span className="mastery-report-stars" role="img" aria-label={t('{0} out of 4 stars', [level])}>
    {[1, 2, 3, 4].map(star => <span key={star} aria-hidden="true" className={star <= level ? '' : 'is-empty'}>{star <= level ? '★' : '☆'}</span>)}
  </span>
}
