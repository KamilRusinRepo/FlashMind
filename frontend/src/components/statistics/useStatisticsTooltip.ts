import { useCallback, useEffect, useRef, useState } from 'react'
import type { DailyStatistics } from '../../api/statistics'

export type StatisticsTooltipData = { key: string; day: DailyStatistics; anchor: HTMLElement }

export function useStatisticsTooltip() {
  const [active, setActive] = useState<StatisticsTooltipData | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const keepOpen = useCallback(() => {
    if (timer.current !== null) { clearTimeout(timer.current); timer.current = null }
  }, [])
  const close = useCallback(() => { keepOpen(); setActive(null) }, [keepOpen])
  const closeLater = useCallback(() => { keepOpen(); timer.current = setTimeout(close, 150) }, [keepOpen, close])
  const open = useCallback((day: DailyStatistics, anchor: HTMLElement, key: string) => {
    keepOpen(); setActive({ day, anchor, key })
  }, [keepOpen])
  useEffect(() => () => keepOpen(), [keepOpen])

  useEffect(() => {
    if (!active) return
    function escape(event: KeyboardEvent) { if (event.key === 'Escape') close() }
    function outside(event: PointerEvent) {
      if (event.target instanceof Node && active?.anchor.contains(event.target)) return
      if (event.target instanceof Element && event.target.closest('.statistics-chart-tooltip')) return
      close()
    }
    function scroll(event: Event) {
      if (event.target instanceof Element && event.target.closest('.statistics-chart-tooltip')) return
      if (active && document.activeElement === active.anchor) {
        const bounds = active.anchor.getBoundingClientRect()
        if (bounds.bottom > 0 && bounds.top < window.innerHeight && bounds.right > 0 && bounds.left < window.innerWidth) {
          // Keyboard focus can scroll the calendar; keep its tooltip aligned.
          setActive(current => current ? { ...current } : null)
          return
        }
      }
      close()
    }
    document.addEventListener('keydown', escape)
    document.addEventListener('pointerdown', outside)
    window.addEventListener('scroll', scroll, true)
    window.addEventListener('resize', close)
    return () => {
      document.removeEventListener('keydown', escape)
      document.removeEventListener('pointerdown', outside)
      window.removeEventListener('scroll', scroll, true)
      window.removeEventListener('resize', close)
      keepOpen()
    }
  }, [active, close, keepOpen])

  return { active, open, closeLater, keepOpen }
}
