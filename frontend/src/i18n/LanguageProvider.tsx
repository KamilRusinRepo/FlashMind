import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { LanguageContext, type Language, type Translation } from './LanguageContext'
import { translate } from './translate'
import { formatCount, type CountUnit } from './count'

const storageKey = 'flashmind.language'
function initialLanguage(): Language {
  try { return localStorage.getItem(storageKey) === 'pl' ? 'pl' : 'en' } catch { return 'en' }
}
function pageTitle(path: string): string {
  const titles: Record<string, string> = {
    '/': 'Master your learning', '/login': 'Sign in', '/register': 'Create account',
    '/forgot-password': 'Forgot password', '/reset-password': 'Reset password', '/status': 'Connection status',
    '/verify-email': 'Confirm email', '/confirm-email-change': 'Confirm email change',
    '/dashboard': 'Dashboard', '/decks': 'My Decks', '/decks/create': 'Create Deck', '/settings': 'Settings', '/statistics': 'Statistics', '/statistics/decks': 'Deck statistics',
  }
  return titles[path] ?? (/^\/statistics\/decks\/[^/]+$/.test(path) ? 'Mastery Report' : /^\/decks\/\d+\/study(?:\/\d+)?$/.test(path) ? 'Study session'
    : /^\/decks\/\d+\/edit$/.test(path) ? 'Edit Deck' : /^\/decks\/\d+$/.test(path) ? 'Deck details' : 'Page not found')
}

export default function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setCurrentLanguage] = useState<Language>(initialLanguage)
  const { pathname } = useLocation()
  const t: Translation = useCallback((text: string | null | undefined, values?: readonly unknown[]) => translate(text ?? '', language, values), [language])
  const count = useCallback((value: number, unit: CountUnit) => formatCount(value, unit, language), [language])
  const setLanguage = useCallback((next: Language) => {
    document.documentElement.lang = next
    setCurrentLanguage(next)
    try { localStorage.setItem(storageKey, next) } catch { /* Use the choice for this tab if storage is unavailable. */ }
  }, [])
  useEffect(() => {
    document.documentElement.lang = language
    document.title = `${translate(pageTitle(pathname), language)} | FlashMind`
  }, [language, pathname])
  useEffect(() => {
    function sync(event: StorageEvent) {
      if (event.key !== storageKey && event.key !== null) return
      const next = event.newValue === 'pl' ? 'pl' : 'en'
      document.documentElement.lang = next
      setCurrentLanguage(next)
    }
    window.addEventListener('storage', sync)
    return () => window.removeEventListener('storage', sync)
  }, [])
  return <LanguageContext value={{ language, locale: language === 'pl' ? 'pl-PL' : 'en-US', setLanguage, t, count }}>{children}</LanguageContext>
}
