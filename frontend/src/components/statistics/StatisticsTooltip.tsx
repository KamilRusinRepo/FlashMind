import { useLayoutEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useLanguage } from '../../i18n/LanguageContext'
import type { StatisticsTooltipData } from './useStatisticsTooltip'

export default function StatisticsTooltip({ id, active, onEnter, onLeave }: {
  id: string; active: StatisticsTooltipData | null; onEnter: () => void; onLeave: () => void
}) {
  const { t, locale } = useLanguage()
  const ref = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    const node = ref.current
    if (!active || !node) return
    const anchor = active.anchor.getBoundingClientRect()
    const bounds = node.getBoundingClientRect()
    const left = Math.max(12, Math.min(anchor.left + anchor.width / 2 - bounds.width / 2, window.innerWidth - bounds.width - 12))
    const preferredTop = anchor.top >= bounds.height + 22 ? anchor.top - bounds.height - 10 : anchor.bottom + 10
    const top = Math.max(12, Math.min(preferredTop, window.innerHeight - bounds.height - 12))
    node.style.left = `${left}px`
    node.style.top = `${top}px`
    node.style.visibility = 'visible'
  }, [active, locale])

  if (!active) return null
  const { day } = active
  const number = new Intl.NumberFormat(locale)
  const date = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  const accuracy = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(day.studied ? day.correct / day.studied : 0)
  return createPortal(<div ref={ref} id={id} role="tooltip" className="statistics-chart-tooltip" style={{ visibility: 'hidden' }}
    onPointerEnter={onEnter} onPointerLeave={onLeave}>
    <p className="statistics-tooltip-date">{date.format(day.date)}</p>
    <dl>
      <div><dt>{t('Cards studied')}</dt><dd>{number.format(day.studied)}</dd></div>
      <div><dt>{t('Correct answers')}</dt><dd>{number.format(day.correct)}</dd></div>
      <div><dt>{t('Accuracy')}</dt><dd>{accuracy}</dd></div>
      <div className="statistics-tooltip-xp"><dt>{t('XP')}</dt><dd>+{number.format(day.xp)}</dd></div>
    </dl>
  </div>, document.body)
}
