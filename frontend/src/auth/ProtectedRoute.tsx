import { useLanguage } from '../i18n/LanguageContext'
import { Link, Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

export default function ProtectedRoute() {
  const { t } = useLanguage()
  const { status, user, error, refresh } = useAuth()

  if (status === 'loading' || status === 'error') {
    return <main className="auth-page">
      <section className="auth-card" aria-labelledby="session-title" aria-busy={status === 'loading'}>
        <div className="auth-heading"><h1 id="session-title">{status === 'loading' ? t("Checking your session") : t("Unable to check your session")}</h1></div>
        {status === 'loading'
          ? <p className="form-notice" role="status">{t("Please wait while we check if you’re signed in…")}</p>
          : <div className="registration-success">
              <p className="form-notice form-error" role="alert">{t(error)}</p>
              <button className="button button-primary button-full" type="button" onClick={refresh}>{t("Try again")}</button>
              <Link className="button button-outline button-full" to="/">{t("Back to home")}</Link>
            </div>}
      </section>
    </main>
  }

  if (status !== 'authenticated' || !user) return <Navigate to="/login" replace />

  return <Outlet />
}
