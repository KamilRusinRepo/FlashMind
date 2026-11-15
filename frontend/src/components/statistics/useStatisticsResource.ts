import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getStatistics, StatisticsApiError } from '../../api/statistics'

type Resource<T> = { status: 'ready'; data: T } | { status: 'error'; message: string } | { status: 'loading' }
export function useStatisticsResource<T>(path: string, validate: (value: unknown) => value is T) {
  const { user, refresh } = useAuth()
  const [attempt, setAttempt] = useState(0)
  const [saved, setSaved] = useState<{ key: string; state: Resource<T> } | null>(null)
  const key = `${user?.id}:${path}:${attempt}`
  useEffect(() => {
    const controller = new AbortController()
    getStatistics(path, validate, controller.signal).then(data => {
      if (!controller.signal.aborted) setSaved({ key, state: { status: 'ready', data } })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof StatisticsApiError && cause.status === 401) refresh()
      setSaved({ key, state: { status: 'error', message: cause instanceof StatisticsApiError ? cause.message : 'Unable to load statistics. Please try again.' } })
    })
    return () => controller.abort()
  }, [path, validate, key, refresh])
  const state: Resource<T> = saved?.key === key ? saved.state : { status: 'loading' }
  return { state, retry: () => setAttempt(value => value + 1) }
}
