import { useLanguage } from '../../i18n/LanguageContext'
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import Brand from '../Brand'
import { useAuth } from '../../auth/AuthContext'
import homeIcon from '../../assets/icons/sidebar/home.svg'
import decksIcon from '../../assets/icons/sidebar/create_deck.svg'
import exploreIcon from '../../assets/icons/sidebar/explore.svg'
import statsIcon from '../../assets/icons/sidebar/statistics.svg'
import settingsIcon from '../../assets/icons/sidebar/settings.svg'
import './Dashboard.css'

const navigation = [
  { label: 'Home', icon: homeIcon, path: '/dashboard' },
  { label: 'My Decks', icon: decksIcon, path: '/decks' },
  { label: 'Explore Decks', icon: exploreIcon, path: '/explore' },
  { label: 'Statistics', icon: statsIcon, path: '/statistics' },
  { label: 'Settings', icon: settingsIcon, path: '/settings' },
]

function DashboardNavigation({ mobile = false }: { mobile?: boolean }) {
  const { t } = useLanguage()
  return <nav className={mobile ? 'db-mobile-nav' : 'db-nav'} aria-label={mobile ? t("Mobile dashboard navigation") : t("Dashboard navigation")}>
    {navigation.map(item => item.path
      ? <NavLink key={item.label} to={item.path} end={item.path === '/dashboard'} aria-label={t(item.label)} className={({ isActive }) => `db-nav-item${isActive ? ' is-active' : ''}`}>
          <img src={item.icon} alt="" /><span>{t(item.label)}</span>
        </NavLink>
      : <button key={item.label} className="db-nav-item" type="button" disabled title={t("Coming soon")} aria-label={t("{0} — coming soon", [t(item.label)])}>
          <img src={item.icon} alt="" /><span>{t(item.label)}</span><span className="db-sr-only"> {t("— coming soon")}</span>
        </button>)}
  </nav>
}

export default function DashboardLayout() {
  const { t } = useLanguage()
  const [collapsed, setCollapsed] = useState(false)
  const { user, error } = useAuth()
  const { pathname } = useLocation()

  useEffect(() => {
    window.scrollTo(0, 0)
    document.getElementById('dashboard-main')?.focus({ preventScroll: true })
  }, [pathname])

  if (!user) return null

  return <div className={`dashboard-shell${collapsed ? ' db-collapsed' : ''}`}>
    <a className="skip-link" href="#dashboard-main">{t("Skip to content")}</a>
    <aside className="db-sidebar" aria-label={t("Dashboard sidebar")}>
      <div className="db-sidebar-top"><Brand />
        <button className="db-collapse" type="button" aria-expanded={!collapsed} aria-controls="dashboard-sidebar-content"
          aria-label={collapsed ? t("Expand sidebar") : t("Collapse sidebar")} onClick={() => setCollapsed(value => !value)}>
          <span aria-hidden="true">{collapsed ? '›' : '‹'}</span>
        </button>
        <div className="db-mobile-signout"><SignOutButton /></div>
      </div>
      <div className="db-sidebar-content" id="dashboard-sidebar-content">
        <DashboardNavigation />
        <div className="db-sidebar-footer">
          <div className="db-profile">
            <span className="db-avatar" aria-hidden="true">{user.username.slice(0, 2).toUpperCase()}</span>
            <div className="db-profile-copy"><strong title={user.username}>{user.username}</strong><span>{t("Signed in")}</span></div>
          </div>
          <SignOutButton />
        </div>
      </div>
    </aside>
    <main className="db-content" id="dashboard-main" tabIndex={-1}>
      {error && <p className="db-session-error form-notice form-error" role="alert">{t(error)}</p>}
      <Outlet />
    </main>
    <DashboardNavigation mobile />
  </div>
}

function SignOutButton() {
  const { t } = useLanguage()
  const { isLoggingOut, signOut } = useAuth()

  return <button className="db-signout" type="button" disabled={isLoggingOut} onClick={() => void signOut()}
    aria-label={isLoggingOut ? t("Signing out…") : t("Sign out")} title={t("Sign out")} aria-busy={isLoggingOut}>
    <span>{isLoggingOut ? t("Signing out…") : t("Sign out")}</span><span className="db-signout-icon" aria-hidden="true">↪</span>
  </button>
}
