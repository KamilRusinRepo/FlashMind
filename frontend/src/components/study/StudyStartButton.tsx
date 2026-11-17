import { useLanguage } from '../../i18n/LanguageContext'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { startStudySession, StudyApiError, type StudyMode } from '../../api/study'
import { useAuth } from '../../auth/AuthContext'
import StudyModeDialog from './StudyModeDialog'
import './StudyStartButton.css'

export default function StudyStartButton({ deckId, label = 'Start Study', icon, mode, disabled = false }: { deckId: number; label?: string; icon?: string; mode?: StudyMode; disabled?: boolean }) {
  const { t } = useLanguage()
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [choosingMode, setChoosingMode] = useState(false)
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function start(selectedMode: StudyMode) {
    if (submitting.current || disabled) return
    submitting.current = true
    setSending(true)
    setError('')
    try {
      const session = await startStudySession(deckId, selectedMode)
      if (mounted.current) navigate(`/decks/${deckId}/study/${session.id}`)
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof StudyApiError && cause.status === 401) { refresh(); return }
      setError(cause instanceof StudyApiError ? cause.message : 'Unable to start the study session.')
    } finally {
      submitting.current = false
      if (mounted.current) setSending(false)
    }
  }

  return <>
    <button className="button button-primary" type="button" disabled={sending || disabled} aria-busy={sending} aria-haspopup={mode === undefined ? 'dialog' : undefined}
      onClick={() => { if (mode === undefined) { setError(''); setChoosingMode(true) } else void start(mode) }}>{icon && <img src={icon} alt="" />}{sending ? t("Starting…") : t(label)}</button>
    {choosingMode && <StudyModeDialog sending={sending} error={t(error)} onStart={selected => void start(selected)} onClose={() => {
      if (!submitting.current) { setChoosingMode(false); setError('') }
    }} />}
    {error && mode !== undefined && !choosingMode && <p className="form-notice form-error study-start-error" role="alert">{t(error)}</p>}
  </>
}
