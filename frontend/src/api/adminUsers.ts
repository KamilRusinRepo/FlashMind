import { requestWithCsrf } from './csrf'

export type AdminUser = { id: number; username: string; email: string; roles: string[]; enabled: boolean; emailVerified: boolean; createdAt: string }
export type AdminUserSummary = { users: number; activeUsers: number; blockedUsers: number; publicDecks: number }
export type AdminUserPage = { content: AdminUser[]; page: number; size: number; totalElements: number; totalPages: number; summary: AdminUserSummary }
export type AdminUserFilters = { search: string; role: string; status: string }

export class AdminUsersApiError extends Error {
  readonly status: number
  constructor(message: string, status = 0) { super(message); this.name = 'AdminUsersApiError'; this.status = status }
}

function isAdminUser(value: unknown): value is AdminUser {
  if (!value || typeof value !== 'object') return false
  const user = value as Record<string, unknown>
  return Number.isSafeInteger(user.id) && Number(user.id) > 0 && typeof user.username === 'string' && typeof user.email === 'string'
    && Array.isArray(user.roles) && user.roles.every(role => role === 'USER' || role === 'ADMIN')
    && typeof user.enabled === 'boolean' && typeof user.emailVerified === 'boolean'
    && typeof user.createdAt === 'string' && !Number.isNaN(Date.parse(user.createdAt))
}

export async function getAdminUsers(filters: AdminUserFilters, page: number, signal: AbortSignal): Promise<AdminUserPage> {
  const params = new URLSearchParams({ ...filters, page: String(page), size: '5' })
  let response: Response
  try {
    response = await fetch(`/api/admin/users?${params}`, { credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer',
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) })
  } catch { throw new AdminUsersApiError('Unable to load users. Check your connection and try again.') }
  if (!response.ok) throw new AdminUsersApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 403 ? 'You do not have permission to access this resource.' : 'Unable to load users. Please try again.', response.status)
  const data = await response.json().catch(() => null)
  const summary = data?.summary
  if (response.status !== 200 || !data || !Array.isArray(data.content) || !data.content.every(isAdminUser)
    || data.page !== page || data.size !== 5 || data.content.length > 5 || !Number.isSafeInteger(data.totalElements) || data.totalElements < data.content.length
    || !Number.isSafeInteger(data.totalPages) || data.totalPages !== Math.ceil(data.totalElements / 5) || !summary
    || !['users', 'activeUsers', 'blockedUsers', 'publicDecks'].every(key => Number.isSafeInteger(summary[key]) && summary[key] >= 0)
    || summary.activeUsers + summary.blockedUsers !== summary.users || data.totalElements > summary.users) {
    throw new AdminUsersApiError('The server returned an unexpected user list. Please try again.')
  }
  return data as AdminUserPage
}

export async function setAdminUserEnabled(userId: number, enabled: boolean): Promise<AdminUser> {
  let response: Response
  try {
    response = await requestWithCsrf(`/api/admin/users/${userId}/enabled`, 'PUT', {
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled }), signal: AbortSignal.timeout(15000),
    })
  } catch { throw new AdminUsersApiError('Unable to confirm account changes. Refresh the user list before trying again.') }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const conflict = response.status === 409 && ['You cannot block your own account.', 'You cannot block the last active administrator.'].includes(data?.message)
    throw new AdminUsersApiError(conflict ? data.message : response.status === 401 ? 'Your session has expired. Please sign in again.'
      : response.status === 403 ? 'You do not have permission to access this resource.'
        : response.status === 404 ? 'This user is no longer available. Refresh the user list.' : 'Unable to change account status. Please try again.', response.status)
  }
  if (response.status !== 200 || !isAdminUser(data) || data.id !== userId || data.enabled !== enabled) {
    throw new AdminUsersApiError('Unable to confirm account changes. Refresh the user list before trying again.')
  }
  return data
}
