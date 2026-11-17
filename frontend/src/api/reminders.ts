import { requestWithCsrf } from './csrf'

export type ReminderSettings = { enabled: boolean; sendHour: number; timezone: string }
export class ReminderSettingsError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'ReminderSettingsError'; this.status = status }
}
async function readSettings(response: Response): Promise<ReminderSettings> {
  if (!response.ok) throw new ReminderSettingsError(response.status === 401 ? 'Your session has expired. Please sign in again.' : 'Unable to load or save reminder settings. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  if (response.status !== 200 || typeof data?.enabled !== 'boolean' || !Number.isInteger(data?.sendHour)
    || data.sendHour < 0 || data.sendHour > 23 || typeof data?.timezone !== 'string' || !data.timezone) {
    throw new ReminderSettingsError('The reminder settings could not be confirmed. Reload them to check the saved state.')
  }
  return data as ReminderSettings
}
export async function getReminderSettings(signal: AbortSignal): Promise<ReminderSettings> {
  let response: Response
  try {
    response = await fetch('/api/users/me/reminders', { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new ReminderSettingsError('Unable to load reminder settings. Check your connection and try again.') }
  return readSettings(response)
}
export async function updateReminderSettings(enabled: boolean): Promise<ReminderSettings> {
  let response: Response
  try {
    response = await requestWithCsrf('/api/users/me/reminders', 'PUT', { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled }), signal: AbortSignal.timeout(15000) })
  } catch { throw new ReminderSettingsError('Saving reminders could not be confirmed. Reload their settings before trying again.') }
  const saved = await readSettings(response)
  if (saved.enabled !== enabled) throw new ReminderSettingsError('The reminder settings could not be confirmed. Reload them to check the saved state.')
  return saved
}
