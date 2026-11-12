import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import Brand from '../Brand'
import { dashboardProfile } from '../../data/dashboard'
import homeIcon from '../../assets/icons/sidebar/home.svg'
import decksIcon from '../../assets/icons/sidebar/create_deck.svg'
import exploreIcon from '../../assets/icons/sidebar/explore.svg'
import statsIcon from '../../assets/icons/sidebar/statistics.svg'
import settingsIcon from '../../assets/icons/sidebar/settings.svg'
import './Dashboard.css'

const navigation = [
  { label: 'Home', icon: homeIcon, path: '/dashboard' },
  { label: 'My Decks', icon: decksIcon },
  { label: 'Explore Decks', icon: exploreIcon },
  { label: 'Statistics', icon: statsIcon },
  { label: 'Settings', icon: settingsIcon },
]

function DashboardNavigation({ mobile = false }: { mobile?: boolean }) {
  return <nav className={mobile ? 'db-mobile-nav' : 'db-nav'} aria-label={mobile ? 'Mobile dashboard navigation' : 'Dashboard navigation'}>
    {navigation.map(item => item.path
      ? <NavLink key={item.label} to={item.path} end aria-label={item.label} className={({ isActive }) => `db-nav-item${isActive ? ' is-active' : ''}`}>
          <img src={item.icon} alt="" /><span>{item.label}</span>
        </NavLink>
      : <button key={item.label} className="db-nav-item" type="button" disabled title="Coming soon" aria-label={`${item.label} — coming soon`}>
          <img src={item.icon} alt="" /><span>{item.label}</span><span className="db-sr-only"> — coming soon</span>
        </button>)}
  </nav>
}

export default function DashboardLayout() {
  const [collapsed, setCollapsed] = useState(false)

  useEffect(() => { document.title = 'Dashboard | FlashMind' }, [])

  return <div className={`dashboard-shell${collapsed ? ' db-collapsed' : ''}`}>
    <a className="skip-link" href="#dashboard-main">Skip to content</a>
    <aside className="db-sidebar" aria-label="Dashboard sidebar">
      <div className="db-sidebar-top"><Brand />
        <button className="db-collapse" type="button" aria-expanded={!collapsed} aria-controls="dashboard-sidebar-content"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={() => setCollapsed(value => !value)}>
          <span aria-hidden="true">{collapsed ? '›' : '‹'}</span>
        </button>
      </div>
      <div className="db-sidebar-content" id="dashboard-sidebar-content">
        <DashboardNavigation />
        <div className="db-sidebar-footer">
          <div className="db-profile">
            <span className="db-avatar" aria-hidden="true">{dashboardProfile.initials}</span>
            <div className="db-profile-copy"><strong>{dashboardProfile.name}</strong><span>{dashboardProfile.label}</span></div>
          </div>
          <button className="db-signout" type="button" disabled title="Sign out will be connected in the authentication step">Sign out <span>Coming soon</span></button>
        </div>
      </div>
    </aside>
    <main className="db-content" id="dashboard-main" tabIndex={-1}><Outlet /></main>
    <DashboardNavigation mobile />
  </div>
}
