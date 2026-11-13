import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import Brand from './Brand'
import { useAuth } from '../auth/AuthContext'

export default function PublicLayout() {
  const { pathname, hash } = useLocation()
  const { status, user, error, isLoggingOut, signOut, refresh } = useAuth()

  useEffect(() => {
    const titles: Record<string, string> = {
      '/': 'Master your learning',
      '/login': 'Sign in',
      '/register': 'Create account',
      '/forgot-password': 'Forgot password',
      '/reset-password': 'Reset password',
      '/status': 'Connection status',
      '/verify-email': 'Confirm email',
    }
    document.title = `${titles[pathname] ?? 'Page not found'} | FlashMind`
    if (hash) {
      document.getElementById(hash.slice(1))?.scrollIntoView()
    } else {
      window.scrollTo(0, 0)
    }
    document.getElementById('main-content')?.focus({ preventScroll: true })
  }, [pathname, hash])

  return (
    <div className="public-layout">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="site-header">
        <Brand />
        <nav className="site-nav" aria-label="Main navigation">
          {status === 'loading' ? <span className="session-label" role="status">Checking session…</span>
            : status === 'error' ? <button className="button button-outline button-small" type="button" onClick={refresh}>Retry session check</button>
            : user ? <>
              <span className="session-user" title={user.username}>{user.username}</span>
              <button type="button" className="button button-outline button-small" disabled={isLoggingOut} onClick={() => void signOut()}>
                {isLoggingOut ? 'Signing out…' : 'Sign out'}
              </button>
            </> : <>
              <NavLink to="/login" className="nav-login">Sign in</NavLink>
              <NavLink to="/register" className="button button-primary button-small">Create account</NavLink>
            </>}
        </nav>
      </header>
      {error && <p className="session-error form-notice form-error" role="alert">{error}</p>}
      <main id="main-content" className="page-content" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <p>FlashMind <span aria-hidden="true">·</span> A little practice. A lasting memory.</p>
        <Link to="/">Home</Link>
      </footer>
    </div>
  )
}
