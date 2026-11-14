import { createContext, useContext } from 'react'
import type { CountUnit } from './count'

export type Language = 'en' | 'pl'
export type Translation = (text: string | null | undefined, values?: readonly unknown[]) => string
export const LanguageContext = createContext<{
  language: Language; locale: string; setLanguage: (language: Language) => void; t: Translation; count: (value: number, unit: CountUnit) => string
} | null>(null)

export function useLanguage() {
  const context = useContext(LanguageContext)
  if (!context) throw new Error('useLanguage requires LanguageProvider.')
  return context
}
