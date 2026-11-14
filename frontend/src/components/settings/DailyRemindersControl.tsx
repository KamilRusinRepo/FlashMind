import { useEffect, useRef, useState } from 'react'
import { getReminderSettings, updateReminderSettings, ReminderSettingsError, type ReminderSettings } from '../../api/reminders'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import reminderIcon from '../../assets/icons/settings/reminder.svg'

export default function DailyRemindersControl() {
  const { t } = useLanguage()
  const { refresh, isLoggingOut } = useAuth()
  const [settings, setSettings] = useState<ReminderSettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const mounted = useRef(true)
  const busy = useRef(false)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    const controller = new AbortController()
    getReminderSettings(controller.signal).then(data => {
      if (!controller.signal.aborted) { setSettings(data); setLoading(false) }
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof ReminderSettingsError && cause.status === 401) { refresh(); return }
      setError(cause instanceof Error ? cause.message : 'Unable to load reminder settings. Check your connection and try again.'); setLoading(false)
    })
    return () => controller.abort()
  }, [attempt, refresh])

  async function save(enabled: boolean) {
    if (busy.current || loading || isLoggingOut || !settings) return
    busy.current = true; setSending(true); setError(''); setNotice('')
    try {
      const saved = await updateReminderSettings(enabled)
      if (!mounted.current) return
      setSettings(saved)
      setNotice(saved.enabled ? 'Daily reminders enabled.' : 'Daily reminders disabled.')
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof ReminderSettingsError && cause.status === 401) { refresh(); return }
      setError(cause instanceof Error ? cause.message : 'Unable to load or save reminder settings. Please try again.')
    } finally { busy.current = false; if (mounted.current) setSending(false) }
  }

  return <div className="settings-reminders" aria-busy={loading || sending}>
    <div className="settings-preference-row">
      <span className="settings-row-icon"><img src={reminderIcon} alt="" /></span>
      <div className="settings-row-copy"><h3 id="settings-reminders-label">{t('Daily Reminders')}</h3>
        <p id="settings-reminders-description">{settings ? t('Reminder after {0}:00 ({1}) if you have cards to review and have not studied today.', [String(settings.sendHour).padStart(2, '0'), settings.timezone]) : loading ? t('Loading reminder settings…') : t('Reminder settings are unavailable.')}</p></div>
      <label className="settings-toggle">
        <input type="checkbox" role="switch" checked={settings?.enabled ?? false} disabled={loading || sending || isLoggingOut || !settings}
          onChange={event => void save(event.target.checked)} aria-labelledby="settings-reminders-label" aria-describedby="settings-reminders-description" />
        <span aria-hidden="true" />
      </label>
    </div>
    {sending && <p className="settings-preview-note" role="status">{t('Saving…')}</p>}
    {notice && <p className="settings-preview-note" role="status">{t(notice)}</p>}
    {error && <div className="settings-reminder-error"><p className="form-notice form-error" role="alert">{t(error)}</p>
      <button className="button button-outline" type="button" disabled={loading || sending} onClick={() => { setLoading(true); setError(''); setNotice(''); setAttempt(value => value + 1) }}>{t('Reload reminder settings')}</button></div>}
  </div>
}
