import { useLanguage } from '../i18n/LanguageContext'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useTheme } from '../theme/ThemeContext'
import { ProfileApiError } from '../api/profile'
import FormField from '../components/FormField'
import SettingsPasswordDialog from '../components/settings/SettingsPasswordDialog'
import SettingsEmailDialog from '../components/settings/SettingsEmailDialog'
import DailyRemindersControl from '../components/settings/DailyRemindersControl'
import passwordIcon from '../assets/icons/settings/password.svg'
import themeIcon from '../assets/icons/settings/theme.svg'
import languageIcon from '../assets/icons/settings/language.svg'
import './Settings.css'

export default function SettingsPage() {
  const { t, language, setLanguage } = useLanguage()
  const { user, changeUsername, refresh, isLoggingOut } = useAuth()
  const [username, setUsername] = useState(user?.username ?? '')
  const { theme, setTheme } = useTheme()
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
    <header className="settings-header"><h1>{t("Profile Settings")}</h1></header>
    <p className="settings-preview-note">{t('Manage your account, appearance, language and daily study reminders.')}</p>
    <section className="settings-card" aria-label={t("Profile details")}>
      <div className="settings-profile">
        <span className="settings-avatar" aria-hidden="true">{user?.username.slice(0, 2).toUpperCase()}</span>
        <div className="settings-profile-info"><h2>{user?.username}</h2><p>{t("FlashMind Member")}</p></div>
      </div>
      <form className="settings-profile-form" onSubmit={saveProfile} aria-busy={sending}>
        <FormField id="settings-username" label={t("Username")} name="username" value={username} onChange={event => { setUsername(event.target.value); setUsernameError(''); setNotice('') }} error={t(usernameError)} disabled={sending || isLoggingOut} required autoComplete="username" />
        <div className="settings-email-control"><FormField id="settings-email" label={t("Email Address")} name="email" type="email" value={user?.email ?? ''} readOnly hint={t("Your current address remains active until you confirm the new one.")} autoComplete="email" />
          <button className="button button-outline" type="button" disabled={sending || isLoggingOut} aria-haspopup="dialog" onClick={() => setEmailOpen(true)}>{t("Change Email")}</button></div>
        <div className="settings-profile-actions"><button className="button button-primary" type="submit" disabled={sending || isLoggingOut}>{sending ? t("Saving…") : t("Save Changes")}</button></div>
      </form>
      {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
      {notice && <p className="settings-preview-note" role="status">{t(notice)}</p>}
    </section>

    <section className="settings-section" aria-labelledby="settings-security-title">
      <h2 id="settings-security-title">{t("Security")}</h2>
      <div className="settings-card settings-preference-row">
        <span className="settings-row-icon"><img src={passwordIcon} alt="" /></span>
        <div className="settings-row-copy"><h3>{t("Password")}</h3><p>{t("Keep your account secure with a strong password.")}</p></div>
        <button className="button button-outline settings-password-button" type="button" aria-haspopup="dialog" onClick={() => setPasswordOpen(true)}>{t("Change Password")}</button>
      </div>
    </section>

    <section className="settings-section" aria-labelledby="settings-preferences-title">
      <h2 id="settings-preferences-title">{t("Preferences")}</h2>
      <div className="settings-card settings-preferences">
        <div className="settings-preference-row">
          <span className="settings-row-icon"><img src={themeIcon} alt="" /></span>
          <div className="settings-row-copy"><h3 id="settings-appearance-label">{t("Appearance")}</h3><p>{t("Choose light or dark theme")}</p></div>
          <div className="settings-segment" role="group" aria-labelledby="settings-appearance-label">
            <button className={theme === 'light' ? 'is-active' : ''} type="button" aria-pressed={theme === 'light'} onClick={() => setTheme('light')}>{t("Light")}</button>
            <button className={theme === 'dark' ? 'is-active' : ''} type="button" aria-pressed={theme === 'dark'} onClick={() => setTheme('dark')}>{t("Dark")}</button>
          </div>
        </div>
        <div className="settings-preference-row">
          <span className="settings-row-icon"><img src={languageIcon} alt="" /></span>
          <div className="settings-row-copy"><h3 id="settings-language-label">{t("Language")}</h3><p>{t("Select your preferred language")}</p></div>
          <select className="settings-language" aria-labelledby="settings-language-label" value={language} onChange={event => setLanguage(event.target.value === 'pl' ? 'pl' : 'en')}>
            <option value="en" lang="en">English</option><option value="pl" lang="pl">Polski</option>
          </select>
        </div>
        <DailyRemindersControl />
      </div>
    </section>
    {passwordOpen && <SettingsPasswordDialog onClose={() => setPasswordOpen(false)} />}
    {emailOpen && <SettingsEmailDialog onClose={() => setEmailOpen(false)} />}
  </div>
}
