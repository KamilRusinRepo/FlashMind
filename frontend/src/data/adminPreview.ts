// Public deck demonstration data only; never send preview identifiers to API endpoints.
export type AdminPreviewDeck = {
  id: string; name: string; author: string | null; deckType: 'GENERAL' | 'LANGUAGE'; cardCount: number; followers: number; createdAt: string
}

export const adminPreviewDecks: AdminPreviewDeck[] = [
  { id: 'preview-deck-1', name: 'English B1', author: 'marek.study', deckType: 'LANGUAGE', cardCount: 52, followers: 18, createdAt: '2026-11-02T10:00:00Z' },
  { id: 'preview-deck-2', name: 'React Essentials', author: 'nina.codes', deckType: 'GENERAL', cardCount: 34, followers: 12, createdAt: '2026-11-04T10:00:00Z' },
  { id: 'preview-deck-3', name: 'Everyday Spanish', author: 'julia.words', deckType: 'LANGUAGE', cardCount: 60, followers: 25, createdAt: '2026-11-06T10:00:00Z' },
  { id: 'preview-deck-4', name: 'Human Biology', author: 'leo.science', deckType: 'GENERAL', cardCount: 28, followers: 8, createdAt: '2026-11-08T10:00:00Z' },
  { id: 'preview-deck-5', name: 'World History', author: null, deckType: 'GENERAL', cardCount: 42, followers: 11, createdAt: '2026-11-10T10:00:00Z' },
  { id: 'preview-deck-6', name: 'German A1', author: 'anna.learns', deckType: 'LANGUAGE', cardCount: 45, followers: 9, createdAt: '2026-11-12T10:00:00Z' },
  { id: 'preview-deck-7', name: 'Literary Terms', author: 'maya.reads', deckType: 'GENERAL', cardCount: 16, followers: 3, createdAt: '2026-11-14T10:00:00Z' },
  { id: 'preview-deck-8', name: 'Travel Vocabulary', author: 'tom.history', deckType: 'LANGUAGE', cardCount: 0, followers: 0, createdAt: '2026-11-16T10:00:00Z' },
]
