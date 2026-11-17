import type { Language } from './LanguageContext'

const words = {
  card: { en: ['card', 'cards', 'cards'], pl: ['fiszka', 'fiszki', 'fiszek'] },
  deck: { en: ['deck', 'decks', 'decks'], pl: ['talia', 'talie', 'talii'] },
  learner: { en: ['learner', 'learners', 'learners'], pl: ['uczący się', 'uczących się', 'uczących się'] },
  day: { en: ['day', 'days', 'days'], pl: ['dzień', 'dni', 'dni'] },
} as const
export type CountUnit = keyof typeof words
export function unitLabel(value: number, unit: CountUnit, language: Language) {
  const category = new Intl.PluralRules(language).select(value)
  const form = category === 'one' ? 0 : category === 'few' ? 1 : 2
  return words[unit][language][form]
}
export function formatCount(value: number, unit: CountUnit, language: Language) {
  return `${new Intl.NumberFormat(language).format(value)} ${unitLabel(value, unit, language)}`
}
