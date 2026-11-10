import { useEffect, useState } from 'react'
import './App.css'

type ApiStatus = {
  status: string
  database: string
  timestamp: string
}

function App() {
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
          throw new Error(`Serwer zwrócił błąd ${response.status}.`)
        }
        const result: ApiStatus = await response.json()
        if (result.status !== 'UP' || result.database !== 'UP') {
          throw new Error('Połączenie z bazą danych jest niedostępne.')
        }
        setStatus(result)
      } catch (cause) {
        if (controller.signal.aborted) return
        setError(cause instanceof Error ? cause.message : 'Nie udało się połączyć z serwerem.')
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
    <main className="start-page">
      <p className="eyebrow">Witaj w</p>
      <h1>FlashMind</h1>
      <p>Aplikacja do nauki z fiszkami.</p>
      <section className="connection-card" aria-labelledby="connection-heading">
        <h2 id="connection-heading">Status połączenia</h2>
        <div role="status" aria-live="polite">
          {loading && <p>Sprawdzanie połączenia…</p>}
          {!loading && error && <p className="connection-error">{error}</p>}
          {!loading && status && (
            <>
              <p className="connection-success">Backend i baza danych działają.</p>
              <p className="checked-at">
                Ostatnie sprawdzenie: {new Date(status.timestamp).toLocaleString('pl-PL')}
              </p>
            </>
          )}
        </div>
        <button type="button" onClick={retry} disabled={loading}>
          Sprawdź ponownie
        </button>
      </section>
    </main>
  )
}

export default App
