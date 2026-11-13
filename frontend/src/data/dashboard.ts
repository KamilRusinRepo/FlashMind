// Only learning statistics and the Explore preview remain sample data.
export type ExploreDeck = {
  id: string
  name: string
  initial: string
  category: string
  tone: 'purple' | 'blue' | 'orange' | 'green'
  cardCount: number
  description: string
  learners: number
  rating: string
}

export const dashboardStats = { streak: 7, studiedToday: 12, dailyGoal: 20, level: 6, xp: 180, nextLevelXp: 420 }

export const exploreDecks: ExploreDeck[] = [
  { id: 'everyday-spanish', name: 'Everyday Spanish', initial: 'S', category: 'Language', tone: 'orange', cardCount: 80,
    description: 'A little Spanish for your next big adventure.', learners: 1240, rating: '4.9' },
  { id: 'react-essentials', name: 'React Essentials', initial: 'R', category: 'Programming', tone: 'blue', cardCount: 48,
    description: 'Build your confidence, one component at a time.', learners: 860, rating: '4.8' },
  { id: 'human-body', name: 'The Human Body', initial: 'B', category: 'Science', tone: 'green', cardCount: 64,
    description: 'Discover the extraordinary science behind you.', learners: 620, rating: '4.9' },
]
