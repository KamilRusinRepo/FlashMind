import { useLanguage } from '../i18n/LanguageContext'
import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import Brand from './Brand'
import { useAuth } from '../auth/AuthContext'

export default function PublicLayout() {
  const { t } = useLanguage()
  const { pathname, hash } = useLocation()
  const { status, user, error, isLoggingOut, signOut, refresh } = useAuth()

  useEffect(() => {
    if (hash) {
      document.getElementById(hash.slice(1))?.scrollIntoView()
    } else {
      window.scrollTo(0, 0)
    }
    document.getElementById('main-content')?.focus({ preventScroll: true })
  }, [pathname, hash])

  return (
    <div className="public-layout">
      <a className="skip-link" href="#main-content">{t("Skip to content")}</a>
      <header className="site-header">
        <Brand />
        <nav className="site-nav" aria-label={t("Main navigation")}>
          {status === 'loading' ? <span className="session-label" role="status">{t("Checking session…")}</span>
            : status === 'error' ? <button className="button button-outline button-small" type="button" onClick={refresh}>{t("Retry session check")}</button>
            : user ? <>
              <span className="session-user" title={user.username}>{user.username}</span>
              <button type="button" className="button button-outline button-small" disabled={isLoggingOut} onClick={() => void signOut()}>
                {isLoggingOut ? t("Signing out…") : t("Sign out")}
              </button>
            </> : <>
              <NavLink to="/login" className="nav-login">{t("Sign in")}</NavLink>
              <NavLink to="/register" className="button button-primary button-small">{t("Create account")}</NavLink>
            </>}
        </nav>
      </header>
      {error && <p className="session-error form-notice form-error" role="alert">{t(error)}</p>}
      <main id="main-content" className="page-content" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <p>FlashMind <span aria-hidden="true">·</span> {t("A little practice. A lasting memory.")}</p>
        <Link to="/">{t("Home")}</Link>
      </footer>
    </div>
  )
}
