export type AuthUser = { id: number; username: string; email: string; roles: string[] }

export function isAuthUser(data: unknown): data is AuthUser {
  if (typeof data !== 'object' || data === null) return false
  const user = data as Record<string, unknown>
  return typeof user.id === 'number' && typeof user.username === 'string' && typeof user.email === 'string'
    && Array.isArray(user.roles) && user.roles.every(role => typeof role === 'string')
}
