import { Link, Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

export default function ProtectedRoute() {
  const { status, user, error, refresh } = useAuth()

  if (status === 'loading' || status === 'error') {
    return <main className="auth-page">
      <section className="auth-card" aria-labelledby="session-title" aria-busy={status === 'loading'}>
        <div className="auth-heading"><h1 id="session-title">{status === 'loading' ? 'Checking your session' : 'Unable to check your session'}</h1></div>
        {status === 'loading'
          ? <p className="form-notice" role="status">Please wait while we check if you’re signed in…</p>
          : <div className="registration-success">
              <p className="form-notice form-error" role="alert">{error}</p>
              <button className="button button-primary button-full" type="button" onClick={refresh}>Try again</button>
              <Link className="button button-outline button-full" to="/">Back to home</Link>
            </div>}
      </section>
    </main>
  }

  if (status !== 'authenticated' || !user) return <Navigate to="/login" replace />

  return <Outlet />
}
