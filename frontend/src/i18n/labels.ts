import type { Translation } from './LanguageContext'

const languages = ['English', 'Polish', 'German', 'Spanish', 'French', 'Japanese']
const categories = ['Academic', 'Language', 'History', 'Science', 'Personal', 'Tech', 'Programming']
// Only predefined labels are translated. Custom metadata remains exactly as authored.
export function languageName(value: string, t: Translation) { return languages.includes(value) ? t(value) : value }
export function categoryName(value: string, t: Translation) { return categories.includes(value) ? t(value) : value }
