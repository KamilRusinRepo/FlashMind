import { useState } from 'react'
import AdminDecksPanel from '../components/admin/AdminDecksPanel'
import type { AdminUserSummary } from '../api/adminUsers'
import AdminUsersPanel from '../components/admin/AdminUsersPanel'
import AdminReviewsPanel from '../components/admin/AdminReviewsPanel'
import { useLanguage } from '../i18n/LanguageContext'
import './AdminPage.css'


export default function AdminPage() {
  const { t, locale } = useLanguage()
  const [section, setSection] = useState<'users' | 'decks' | 'reviews'>('users')
  const [summary, setSummary] = useState<AdminUserSummary | null>(null)
  const number = new Intl.NumberFormat(locale)
  const metrics = [
    { label: 'Users', value: summary?.users },
    { label: 'Active accounts', value: summary?.activeUsers },
    { label: 'Blocked accounts', value: summary?.blockedUsers },
    { label: 'Public Decks', value: summary?.publicDecks },
  ]
  return <div className="db-page admin-page">
    <header className="admin-heading"><p className="db-eyebrow">{t('ADMINISTRATION')}</p><h1>{t('Admin Panel')}</h1><p>{t('Manage users, public decks and reviews.')}</p></header>
    <div className="admin-metrics">{metrics.map(metric => <div className="admin-metric" key={metric.label}><span>{t(metric.label)}</span><strong>{metric.value === undefined ? '—' : number.format(metric.value)}</strong></div>)}</div>
    <nav className="admin-sections" aria-label={t('Administration sections')}>
      <button type="button" aria-pressed={section === 'users'} onClick={() => setSection('users')}>{t('Users')}<span>{summary ? number.format(summary.users) : '—'}</span></button>
      <button type="button" aria-pressed={section === 'decks'} onClick={() => setSection('decks')}>{t('Public Decks')}<span>{summary ? number.format(summary.publicDecks) : '—'}</span></button>
      <button type="button" aria-pressed={section === 'reviews'} onClick={() => setSection('reviews')}>{t('Reviews')}</button>
    </nav>
    {section === 'users' ? <AdminUsersPanel onSummary={setSummary} /> : section === 'decks' ? <AdminDecksPanel onSummary={setSummary} /> : <AdminReviewsPanel />}
  </div>
}
