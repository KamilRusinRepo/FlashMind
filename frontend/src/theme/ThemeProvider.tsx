import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { ThemeContext, type Theme } from './ThemeContext'

const storageKey = 'flashmind.theme'
function apply(theme: Theme) {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setCurrentTheme] = useState<Theme>(() => document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light')
  const setTheme = useCallback((next: Theme) => {
    apply(next)
    setCurrentTheme(next)
    try { localStorage.setItem(storageKey, next) } catch { /* Keep the current tab usable when storage is blocked. */ }
  }, [])

  useEffect(() => {
    function sync(event: StorageEvent) {
      if (event.key !== storageKey && event.key !== null) return
      const next = event.newValue === 'dark' ? 'dark' : 'light'
      apply(next)
      setCurrentTheme(next)
    }
    window.addEventListener('storage', sync)
    return () => window.removeEventListener('storage', sync)
  }, [])

  return <ThemeContext value={{ theme, setTheme }}>{children}</ThemeContext>
}
