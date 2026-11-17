// Filter labels remain separate from the API's public-deck data.
export const exploreCategories = ['Academic', 'Language', 'History', 'Science', 'Personal', 'Tech']
export const exploreLanguages = ['English', 'Polish', 'German', 'Spanish', 'French', 'Japanese']
export const exploreSorts: { value: string; label: string; disabled?: boolean }[] = [
  { value: 'newest', label: 'Newest' }, { value: 'cards', label: 'Most Cards' },
  { value: 'followers', label: 'Most Followed' },
  { value: 'rating', label: 'Highest Rated', disabled: true }, { value: 'reviews', label: 'Most Reviewed', disabled: true },
]
export type ExploreFilters = { q: string; deckType: string; category: string; source: string; target: string; sort: string }
export const emptyExploreFilters: ExploreFilters = { q: '', deckType: '', category: '', source: '', target: '', sort: 'newest' }

export function readExploreFilters(params: URLSearchParams): ExploreFilters {
  const option = (key: string, options: string[]) => {
    const value = params.get(key) ?? ''
    return options.includes(value) ? value : ''
  }
  const deckType = option('deck_type', ['GENERAL', 'LANGUAGE'])
  const text = (key: string) => (params.get(key) ?? '').trim().slice(0, 60)
  return {
    q: (params.get('q') ?? '').trim().slice(0, 120), deckType,
    category: text('category'), source: text('source_language'),
    target: deckType === 'LANGUAGE' ? text('target_language') : '',
    sort: option('sort', exploreSorts.filter(sort => !sort.disabled).map(sort => sort.value)) || 'newest',
  }
}
