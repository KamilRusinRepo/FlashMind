import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ProfileApiError } from '../api/profile'
import FormField from '../components/FormField'
import SettingsPasswordDialog from '../components/settings/SettingsPasswordDialog'
import SettingsEmailDialog from '../components/settings/SettingsEmailDialog'
import passwordIcon from '../assets/icons/settings/password.svg'
import themeIcon from '../assets/icons/settings/theme.svg'
import languageIcon from '../assets/icons/settings/language.svg'
import reminderIcon from '../assets/icons/settings/reminder.svg'
import './Settings.css'

export default function SettingsPage() {
  const { user, changeUsername, refresh, isLoggingOut } = useAuth()
  const [username, setUsername] = useState(user?.username ?? '')
  const [theme, setTheme] = useState<'light' | 'dark'>('light')
  const [language, setLanguage] = useState<'en' | 'pl'>('en')
  const [reminders, setReminders] = useState(true)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [emailOpen, setEmailOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [usernameError, setUsernameError] = useState('')
  const [sending, setSending] = useState(false)
  const busy = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current || isLoggingOut) return
    const value = username.trim()
    const invalid = value.length < 3 || value.length > 50 ? 'Username must contain between 3 and 50 characters.'
      : !/^[a-zA-Z0-9_.-]+$/.test(value) ? 'Username may contain letters, digits, dots, hyphens and underscores.' : ''
    setNotice('')
    setError('')
    setUsernameError(invalid)
    if (invalid) { event.currentTarget.querySelector<HTMLInputElement>('[name="username"]')?.focus(); return }
    busy.current = true
    setSending(true)
    try {
      const saved = await changeUsername(value)
      if (!mounted.current) return
      setUsername(saved.username)
      setNotice('Your username has been updated.')
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof ProfileApiError && cause.status === 401) { refresh(); return }
      setUsernameError(cause instanceof ProfileApiError ? cause.usernameError : '')
      setError(cause instanceof ProfileApiError ? cause.message : 'Unable to save your username. Please try again.')
    } finally {
      busy.current = false
      if (mounted.current) setSending(false)
    }
  }

  return <div className="settings-page">
    <header className="settings-header"><h1>Profile Settings</h1></header>
    <p className="settings-preview-note">You can save your username and request an email change. Password and preference controls are still previews; appearance and language apply only to the controls below.</p>
    <section className="settings-card" aria-label="Profile details">
      <div className="settings-profile">
        <span className="settings-avatar" aria-hidden="true">{user?.username.slice(0, 2).toUpperCase()}</span>
        <div className="settings-profile-info"><h2>{user?.username}</h2><p>FlashMind Member</p></div>
      </div>
      <form className="settings-profile-form" onSubmit={saveProfile} aria-busy={sending}>
        <FormField id="settings-username" label="Username" name="username" value={username} onChange={event => { setUsername(event.target.value); setUsernameError(''); setNotice('') }} error={usernameError} disabled={sending || isLoggingOut} required autoComplete="username" />
        <div className="settings-email-control"><FormField id="settings-email" label="Email Address" name="email" type="email" value={user?.email ?? ''} readOnly hint="Your current address remains active until you confirm the new one." autoComplete="email" />
          <button className="button button-outline" type="button" disabled={sending || isLoggingOut} aria-haspopup="dialog" onClick={() => setEmailOpen(true)}>Change Email</button></div>
        <div className="settings-profile-actions"><button className="button button-primary" type="submit" disabled={sending || isLoggingOut}>{sending ? 'Saving…' : 'Save Changes'}</button></div>
      </form>
      {error && <p className="form-notice form-error" role="alert">{error}</p>}
      {notice && <p className="settings-preview-note" role="status">{notice}</p>}
    </section>

    <section className="settings-section" aria-labelledby="settings-security-title">
      <h2 id="settings-security-title">Security</h2>
      <div className="settings-card settings-preference-row">
        <span className="settings-row-icon"><img src={passwordIcon} alt="" /></span>
        <div className="settings-row-copy"><h3>Password</h3><p>Keep your account secure with a strong password.</p></div>
        <button className="button button-outline settings-password-button" type="button" aria-haspopup="dialog" onClick={() => setPasswordOpen(true)}>Change Password</button>
      </div>
    </section>

    <section className="settings-section" aria-labelledby="settings-preferences-title">
      <h2 id="settings-preferences-title">Preferences</h2>
      <div className="settings-card settings-preferences">
        <div className="settings-preference-row">
          <span className="settings-row-icon"><img src={themeIcon} alt="" /></span>
          <div className="settings-row-copy"><h3 id="settings-appearance-label">Appearance</h3><p>Choose light or dark theme</p></div>
          <div className="settings-segment" role="group" aria-labelledby="settings-appearance-label">
            <button className={theme === 'light' ? 'is-active' : ''} type="button" aria-pressed={theme === 'light'} onClick={() => setTheme('light')}>Light</button>
            <button className={theme === 'dark' ? 'is-active' : ''} type="button" aria-pressed={theme === 'dark'} onClick={() => setTheme('dark')}>Dark</button>
          </div>
        </div>
        <div className="settings-preference-row">
          <span className="settings-row-icon"><img src={languageIcon} alt="" /></span>
          <div className="settings-row-copy"><h3 id="settings-language-label">Language</h3><p>Select your preferred language</p></div>
          <button className="settings-language" type="button" aria-label={`Language: ${language === 'en' ? 'English (US). Switch to Polish' : 'Polski. Switch to English'}`} onClick={() => setLanguage(value => value === 'en' ? 'pl' : 'en')}>{language === 'en' ? 'English (US)' : 'Polski'}</button>
        </div>
        <div className="settings-preference-row">
          <span className="settings-row-icon"><img src={reminderIcon} alt="" /></span>
          <div className="settings-row-copy"><h3 id="settings-reminders-label">Daily Reminders</h3><p id="settings-reminders-description">Get notified for daily practice</p></div>
          <label className="settings-toggle">
            <input type="checkbox" role="switch" checked={reminders} onChange={event => setReminders(event.target.checked)} aria-labelledby="settings-reminders-label" aria-describedby="settings-reminders-description" />
            <span aria-hidden="true" />
          </label>
        </div>
      </div>
    </section>
    {passwordOpen && <SettingsPasswordDialog onClose={() => setPasswordOpen(false)} />}
    {emailOpen && <SettingsEmailDialog onClose={() => setEmailOpen(false)} />}
  </div>
}
