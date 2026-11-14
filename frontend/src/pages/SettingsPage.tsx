import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import SettingsPasswordDialog from '../components/settings/SettingsPasswordDialog'
import passwordIcon from '../assets/icons/settings/password.svg'
import themeIcon from '../assets/icons/settings/theme.svg'
import languageIcon from '../assets/icons/settings/language.svg'
import reminderIcon from '../assets/icons/settings/reminder.svg'
import './Settings.css'

export default function SettingsPage() {
  const { user } = useAuth()
  const [username, setUsername] = useState(user?.username ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [theme, setTheme] = useState<'light' | 'dark'>('light')
  const [language, setLanguage] = useState<'en' | 'pl'>('en')
  const [reminders, setReminders] = useState(true)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [notice, setNotice] = useState('')

  function previewProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setNotice('Profile changes have not been saved. Saving will be available when account settings are connected.')
  }

  return <div className="settings-page">
    <header className="settings-header"><h1>Profile Settings</h1></header>
    <p className="settings-preview-note">Settings preview: edits and preferences are temporary. They are not saved, and appearance and language apply only to the controls below.</p>
    <section className="settings-card" aria-label="Profile details">
      <div className="settings-profile">
        <span className="settings-avatar" aria-hidden="true">{user?.username.slice(0, 2).toUpperCase()}</span>
        <div className="settings-profile-info"><h2>{user?.username}</h2><p>FlashMind Member</p></div>
      </div>
      <form className="settings-profile-form" onSubmit={previewProfile}>
        <FormField id="settings-username" label="Username" name="username" value={username} onChange={event => setUsername(event.target.value)} required autoComplete="username" />
        <FormField id="settings-email" label="Email Address" name="email" type="email" value={email} onChange={event => setEmail(event.target.value)} required autoComplete="email" />
        <div className="settings-profile-actions"><button className="button button-primary" type="submit">Save Changes</button></div>
      </form>
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
  </div>
}
