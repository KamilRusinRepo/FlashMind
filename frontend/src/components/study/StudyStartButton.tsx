import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { startStudySession, StudyApiError } from '../../api/study'
import { useAuth } from '../../auth/AuthContext'
import './StudyStartButton.css'

export default function StudyStartButton({ deckId, label = 'Start Study', icon }: { deckId: number; label?: string; icon?: string }) {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function start() {
    if (submitting.current) return
    submitting.current = true
    setSending(true)
    setError('')
    try {
      const session = await startStudySession(deckId)
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
    <button className="button button-primary" type="button" disabled={sending} aria-busy={sending} onClick={() => void start()}>{icon && <img src={icon} alt="" />}{sending ? 'Starting…' : label}</button>
    {error && <p className="form-notice form-error study-start-error" role="alert">{error}</p>}
  </>
}
