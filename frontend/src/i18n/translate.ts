import { pl } from './pl'
import type { Language } from './LanguageContext'

const patterns = Object.entries(pl).filter(([key]) => /\{\d+\}/.test(key)).map(([key, value]) => ({
  pattern: new RegExp('^' + key.split(/(\{\d+\})/).map(part => /^\{\d+\}$/.test(part) ? '(.*?)' : part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('') + '$'),
  value,
}))
function interpolate(text: string, values: readonly unknown[]) {
  return text.replace(/\{(\d+)\}/g, (match, index: string) => index in values ? String(values[Number(index)]) : match)
}
function polish(text: string): string {
  if (Object.hasOwn(pl, text)) return pl[text]
  for (const entry of patterns) {
    const match = entry.pattern.exec(text)
    if (match) return interpolate(entry.value, match.slice(1))
  }
  // API errors stay canonical in state, so they can also change language while visible.
  const sentences = text.split(/(?<=[.!?])\s+(?=[A-Z])/)
  return sentences.length > 1 ? sentences.map(part => polish(part)).join(' ') : text
}
export function translate(text: string, language: Language, values: readonly unknown[] = []): string {
  return interpolate(language === 'pl' ? polish(text) : text, values)
}
