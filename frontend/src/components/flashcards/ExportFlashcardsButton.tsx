import { useEffect, useRef, useState } from 'react'
import { exportFlashcardsCsv } from '../../api/flashcardCsv'
import { FlashcardApiError } from '../../api/flashcards'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'

export default function ExportFlashcardsButton({ deckId, onError }: { deckId: number; onError: (message: string) => void }) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [sending, setSending] = useState(false)
  const active = useRef<AbortController | null>(null)
  useEffect(() => () => active.current?.abort(), [])

  async function download() {
    if (active.current) return
    const controller = new AbortController()
    active.current = controller; setSending(true); onError('')
    try {
      const blob = await exportFlashcardsCsv(deckId, controller.signal)
      if (controller.signal.aborted) return
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url; link.download = `deck-${deckId}-flashcards.csv`
      document.body.appendChild(link)
      try { link.click() }
      finally { link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
    } catch (cause) {
      if (controller.signal.aborted) return
      if (cause instanceof FlashcardApiError && cause.status === 401) { refresh(); return }
      onError(cause instanceof FlashcardApiError ? cause.message : 'Unable to export flashcards. Please try again.')
    } finally {
      if (!controller.signal.aborted) setSending(false)
      if (active.current === controller) active.current = null
    }
  }

  return <button className="button button-outline" type="button" disabled={sending} aria-busy={sending}
    onClick={() => void download()}>{sending ? t('Exporting CSV…') : t('Export CSV')}</button>
}
