import badIcon from '../assets/icons/statistics/bad.svg'
import averageIcon from '../assets/icons/statistics/average.svg'
import goodIcon from '../assets/icons/statistics/good.svg'
import veryGoodIcon from '../assets/icons/statistics/very_good.svg'

export type MasteryLevel = 1 | 2 | 3 | 4
export const masteryRatings = [
  { level: 1, label: 'Bad', tone: 'bad', icon: badIcon },
  { level: 2, label: 'Average', tone: 'average', icon: averageIcon },
  { level: 3, label: 'Good', tone: 'good', icon: goodIcon },
  { level: 4, label: 'Very good', tone: 'great', icon: veryGoodIcon },
] as const

const englishWords = [
  ['Door', 'Drzwi'], ['Apple', 'Jabłko'], ['Book', 'Książka'], ['Window', 'Okno'],
  ['Garden', 'Ogród'], ['Journey', 'Podróż'], ['Curiosity', 'Ciekawość'], ['Remember', 'Pamiętać'],
  ['Discover', 'Odkrywać'], ['Knowledge', 'Wiedza'], ['Practice', 'Ćwiczyć'], ['Confidence', 'Pewność siebie'],
]
const spanishWords = [
  ['Hola', 'Cześć'], ['Gracias', 'Dziękuję'], ['Casa', 'Dom'], ['Agua', 'Woda'],
  ['Libro', 'Książka'], ['Amigo', 'Przyjaciel'], ['Escuela', 'Szkoła'], ['Comida', 'Jedzenie'],
]

export type SampleMasteryCard = {
  id: number
  question: string
  answer: string
  level: MasteryLevel
  lastReviewedDaysAgo: number | null
  nextReviewInDays: number | null
}

// Preview cards only; sample slugs are deliberately separate from real deck IDs.
export function createSampleMasteryCards(deck: { slug: string; total: number; mastered: number }): SampleMasteryCard[] {
  const words = deck.slug.includes('spanish') ? spanishWords : englishWords
  return Array.from({ length: deck.total }, (_, index) => {
    const level: MasteryLevel = index < deck.mastered ? 4 : ((index - deck.mastered) % 3 + 1) as MasteryLevel
    const [question, answer] = words[index % words.length]
    const repetition = Math.floor(index / words.length)
    return {
      id: index + 1,
      question: repetition ? `${question} (${repetition + 1})` : question,
      answer,
      level,
      lastReviewedDaysAgo: level === 1 && index % 2 === 0 ? null : index % 8,
      nextReviewInDays: level === 1 ? null : level === 2 ? 1 : level === 3 ? 7 : 14,
    }
  })
}
