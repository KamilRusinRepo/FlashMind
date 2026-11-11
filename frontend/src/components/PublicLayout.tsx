import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import Brand from './Brand'

export default function PublicLayout() {
  const { pathname, hash } = useLocation()

  useEffect(() => {
    const titles: Record<string, string> = {
      '/': 'Master your learning',
      '/login': 'Sign in',
      '/register': 'Create account',
      '/status': 'Connection status',
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
          <NavLink to="/login" className="nav-login">Sign in</NavLink>
          <NavLink to="/register" className="button button-primary button-small">Create account</NavLink>
        </nav>
      </header>
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
