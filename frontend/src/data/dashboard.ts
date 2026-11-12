// Preview data only. Replace with API responses when decks and statistics are implemented.
export type DashboardDeck = {
  id: string
  name: string
  initial: string
  category: string
  tone: 'purple' | 'blue' | 'orange' | 'green'
  cardCount: number
  mastery: number
  due: number
}

export type ExploreDeck = DashboardDeck & {
  description: string
  learners: number
  rating: string
}

export const dashboardProfile = { name: 'Alex Morgan', initials: 'AM', label: 'Sample learner' }

export const dashboardStats = { streak: 7, studiedToday: 12, dailyGoal: 20, level: 6, xp: 180, nextLevelXp: 420 }

export const myDecks: DashboardDeck[] = [
  { id: 'english-a1', name: 'English A1', initial: 'E', category: 'Language', tone: 'purple', cardCount: 52, mastery: 46, due: 18 },
  { id: 'java-fundamentals', name: 'Java Fundamentals', initial: 'J', category: 'Programming', tone: 'blue', cardCount: 40, mastery: 65, due: 8 },
  { id: 'world-history', name: 'World History', initial: 'H', category: 'History', tone: 'orange', cardCount: 36, mastery: 28, due: 0 },
]

export const continueDecks = myDecks.filter(deck => deck.due > 0)

export const exploreDecks: ExploreDeck[] = [
  { id: 'everyday-spanish', name: 'Everyday Spanish', initial: 'S', category: 'Language', tone: 'orange', cardCount: 80, mastery: 0, due: 0,
    description: 'A little Spanish for your next big adventure.', learners: 1240, rating: '4.9' },
  { id: 'react-essentials', name: 'React Essentials', initial: 'R', category: 'Programming', tone: 'blue', cardCount: 48, mastery: 0, due: 0,
    description: 'Build your confidence, one component at a time.', learners: 860, rating: '4.8' },
  { id: 'human-body', name: 'The Human Body', initial: 'B', category: 'Science', tone: 'green', cardCount: 64, mastery: 0, due: 0,
    description: 'Discover the extraordinary science behind you.', learners: 620, rating: '4.9' },
]
