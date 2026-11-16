// UI preview only: these are not decks/accounts from the application's database.
export type ExploreDeckPreview = {
  id: string; name: string; description: string; author: string | null
  deckType: 'GENERAL' | 'LANGUAGE'; category: string; sourceLanguage: string; targetLanguage: string | null
  cardCount: number; learners: number; rating: number; reviews: number; createdAt: string
}
export const exploreCategories = ['Academic', 'Language', 'History', 'Science', 'Personal', 'Tech']
export const exploreLanguages = ['English', 'Polish', 'German', 'Spanish', 'French', 'Japanese']
export const exploreSorts = [
  { value: 'followers', label: 'Most Followed' }, { value: 'cards', label: 'Most Cards' },
  { value: 'rating', label: 'Highest Rated' }, { value: 'reviews', label: 'Most Reviewed' }, { value: 'newest', label: 'Newest' },
]
export type ExploreFilters = { q: string; deckType: string; category: string; source: string; target: string; sort: string }
export const emptyExploreFilters: ExploreFilters = { q: '', deckType: '', category: '', source: '', target: '', sort: 'followers' }

export function readExploreFilters(params: URLSearchParams): ExploreFilters {
  const option = (key: string, options: string[]) => {
    const value = params.get(key) ?? ''
    return options.includes(value) ? value : ''
  }
  const deckType = option('deck_type', ['GENERAL', 'LANGUAGE'])
  return {
    q: (params.get('q') ?? '').trim().slice(0, 120), deckType,
    category: option('category', exploreCategories), source: option('source_language', exploreLanguages),
    target: deckType === 'LANGUAGE' ? option('target_language', exploreLanguages) : '',
    sort: option('sort', exploreSorts.map(sort => sort.value)) || 'followers',
  }
}

export const explorePreviewDecks: ExploreDeckPreview[] = [
  { id: 'preview-english-a1', name: 'English A1', description: 'Everyday words to build your confidence in English.', author: 'Anna Nowak', deckType: 'LANGUAGE', category: 'Language', sourceLanguage: 'English', targetLanguage: 'Polish', cardCount: 52, learners: 1240, rating: 4.8, reviews: 82, createdAt: '2026-10-02' },
  { id: 'preview-english-b1', name: 'English B1', description: 'Take the next step with useful intermediate vocabulary.', author: 'Marta K.', deckType: 'LANGUAGE', category: 'Language', sourceLanguage: 'English', targetLanguage: 'Polish', cardCount: 51, learners: 860, rating: 4.7, reviews: 46, createdAt: '2026-10-20' },
  { id: 'preview-spanish', name: 'Everyday Spanish', description: 'A little Spanish for your next big adventure.', author: 'Carlos R.', deckType: 'LANGUAGE', category: 'Language', sourceLanguage: 'Spanish', targetLanguage: 'English', cardCount: 80, learners: 720, rating: 4.9, reviews: 63, createdAt: '2026-10-14' },
  { id: 'preview-java', name: 'Java Essentials', description: 'Classes, collections and the ideas behind object-oriented programming.', author: 'Adam W.', deckType: 'GENERAL', category: 'Tech', sourceLanguage: 'English', targetLanguage: null, cardCount: 36, learners: 610, rating: 4.6, reviews: 28, createdAt: '2026-11-05' },
  { id: 'preview-body', name: 'The Human Body', description: 'Discover the extraordinary science behind you.', author: 'Zofia M.', deckType: 'GENERAL', category: 'Science', sourceLanguage: 'Polish', targetLanguage: null, cardCount: 64, learners: 430, rating: 4.8, reviews: 32, createdAt: '2026-10-28' },
  { id: 'preview-french', name: 'French for Travel', description: 'From your first bonjour to ordering your favourite meal.', author: 'Lila B.', deckType: 'LANGUAGE', category: 'Language', sourceLanguage: 'French', targetLanguage: 'Polish', cardCount: 42, learners: 350, rating: 4.5, reviews: 19, createdAt: '2026-11-10' },
  { id: 'preview-history', name: 'World History', description: 'Important moments, people and ideas across the centuries.', author: null, deckType: 'GENERAL', category: 'History', sourceLanguage: 'Polish', targetLanguage: null, cardCount: 60, learners: 280, rating: 4.4, reviews: 15, createdAt: '2026-09-18' },
  { id: 'preview-japanese', name: 'Japanese Starter', description: 'Learn your first words and make room for a new language.', author: 'Yuki S.', deckType: 'LANGUAGE', category: 'Language', sourceLanguage: 'Japanese', targetLanguage: 'English', cardCount: 24, learners: 190, rating: 4.9, reviews: 11, createdAt: '2026-11-15' },
  { id: 'preview-biology', name: 'Biology Exam Prep', description: 'Key concepts for a calmer, more confident exam day.', author: 'Julia P.', deckType: 'GENERAL', category: 'Academic', sourceLanguage: 'German', targetLanguage: null, cardCount: 95, learners: 120, rating: 4.3, reviews: 8, createdAt: '2026-11-12' },
]
