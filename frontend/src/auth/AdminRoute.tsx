import { useEffect, useState } from 'react'
import { Link, Navigate, Outlet } from 'react-router'
import { AdminAccessError, checkAdminAccess } from '../api/adminAccess'
import { useLanguage } from '../i18n/LanguageContext'
import { useAuth } from './AuthContext'

/** Nested under ProtectedRoute, which handles session loading, errors and anonymous visitors. */
export default function AdminRoute() {
  const { user } = useAuth()
  if (!user?.roles.includes('ADMIN')) return <Navigate to="/dashboard" replace />
  return <AdminAccessCheck key={user.id} />
}

type AccessState = { status: 'loading' } | { status: 'allowed' } | { status: 'forbidden' } | { status: 'error'; message: string }

function AdminAccessCheck() {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<AccessState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    checkAdminAccess(controller.signal).then(() => {
      if (!controller.signal.aborted) setState({ status: 'allowed' })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof AdminAccessError && cause.status === 401) { refresh(); return }
      if (cause instanceof AdminAccessError && cause.status === 403) { setState({ status: 'forbidden' }); return }
      setState({ status: 'error', message: cause instanceof AdminAccessError ? cause.message : 'Unable to check administrator access. Please try again.' })
    })
    return () => controller.abort()
  }, [attempt, refresh])

  if (state.status === 'allowed') return <Outlet />
  if (state.status === 'forbidden') return <Navigate to="/dashboard" replace />
  return <section className="decks-message" aria-busy={state.status === 'loading'}>
    <h1>{t('Checking administrator access')}</h1>
    {state.status === 'loading' ? <p role="status">{t('Please wait while we check your permissions…')}</p>
      : <>
        <p className="form-notice form-error" role="alert">{t(state.message)}</p>
        <button className="button button-primary" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button>
        <Link className="button button-outline" to="/dashboard">{t('Dashboard')}</Link>
      </>}
  </section>
}
