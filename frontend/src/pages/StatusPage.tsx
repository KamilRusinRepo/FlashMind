import { useLanguage } from '../i18n/LanguageContext'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

type ApiStatus = {
  status: string
  database: string
  timestamp: string
}

function StatusPage() {
  const { t, locale } = useLanguage()
  const [status, setStatus] = useState<ApiStatus | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    async function checkConnection() {
      try {
        const response = await fetch('/api/status', {
          signal: controller.signal,
          cache: 'no-store',
        })
        if (!response.ok) {
          throw new Error(`The server returned error ${response.status}.`)
        }
        const result: ApiStatus = await response.json()
        if (result.status !== 'UP' || result.database !== 'UP') {
          throw new Error('The database connection is unavailable.')
        }
        setStatus(result)
      } catch (cause) {
        if (controller.signal.aborted) return
        setError(cause instanceof Error ? cause.message : 'Unable to reach the server.')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    void checkConnection()
    return () => controller.abort()
  }, [attempt])

  function retry() {
    setLoading(true)
    setError('')
    setStatus(null)
    setAttempt((value) => value + 1)
  }

  return (
    <section className="status-page">
      <p className="eyebrow">{t("Welcome to")}</p>
      <h1>FlashMind</h1>
      <p>{t("A flashcard learning application.")}</p>
      <section className="connection-card" aria-labelledby="connection-heading">
        <h2 id="connection-heading">{t("Connection status")}</h2>
        <div role="status" aria-live="polite">
          {loading && <p>{t("Checking connection…")}</p>}
          {!loading && error && <p className="connection-error">{t(error)}</p>}
          {!loading && status && (
            <>
              <p className="connection-success">{t("The backend and database are running.")}</p>
              <p className="checked-at">{t("Last checked:")} {new Date(status.timestamp).toLocaleString(locale)}
              </p>
            </>
          )}
        </div>
        <button type="button" onClick={retry} disabled={loading}>{t("Check again")}</button>
      </section>
    <Link className="text-link" to="/">{t("Back to home")}</Link></section>
  )
}

export default StatusPage
