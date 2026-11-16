import { useSearchParams } from 'react-router'
import { useLanguage } from '../i18n/LanguageContext'
import { categoryName, languageName } from '../i18n/labels'
import { explorePreviewDecks, readExploreFilters, type ExploreFilters as Filters } from '../data/explore'
import ExploreFilters from '../components/explore/ExploreFilters'
import ExploreDeckCard from '../components/explore/ExploreDeckCard'
import './ExploreDecks.css'

export default function ExploreDecksPage() {
  const { t, locale, count } = useLanguage()
  const [params, setParams] = useSearchParams()
  const filters = readExploreFilters(params)
  function apply(next: Filters) {
    const query = new URLSearchParams()
    if (next.q.trim()) query.set('q', next.q.trim())
    if (next.deckType) query.set('deck_type', next.deckType)
    if (next.category) query.set('category', next.category)
    if (next.source) query.set('source_language', next.source)
    if (next.deckType === 'LANGUAGE' && next.target) query.set('target_language', next.target)
    if (next.sort !== 'followers') query.set('sort', next.sort)
    setParams(query)
  }
  const search = filters.q.toLocaleLowerCase(locale)
  const decks = explorePreviewDecks.filter(deck => {
    const text = [deck.name, deck.description, deck.author ?? t('Deleted user'), deck.category,
      categoryName(deck.category, t), deck.sourceLanguage, languageName(deck.sourceLanguage, t), deck.targetLanguage ?? '',
      deck.targetLanguage ? languageName(deck.targetLanguage, t) : ''].join(' ').toLocaleLowerCase(locale)
    return text.includes(search) && (!filters.deckType || deck.deckType === filters.deckType)
      && (!filters.category || deck.category === filters.category) && (!filters.source || deck.sourceLanguage === filters.source)
      && (!filters.target || deck.targetLanguage === filters.target)
  }).toSorted((a, b) => {
    const difference = filters.sort === 'cards' ? b.cardCount - a.cardCount : filters.sort === 'rating' ? b.rating - a.rating
      : filters.sort === 'reviews' ? b.reviews - a.reviews : filters.sort === 'newest' ? b.createdAt.localeCompare(a.createdAt) : b.learners - a.learners
    return difference || a.name.localeCompare(b.name, locale)
  })

  return <div className="explore-page">
    <header className="explore-heading"><h1>{t('Explore Marketplace')}</h1><p>{t('Find public decks created by other learners.')}</p></header>
    <p className="explore-preview"><strong>{t('Preview')}</strong> {t('Decks and creators below are sample data. Following and deck details will be connected later.')}</p>
    <ExploreFilters key={params.toString()} initial={filters} onApply={apply} />
    <section aria-labelledby="explore-trending-title">
      <div className="explore-results-heading"><h2 id="explore-trending-title">{t('Trending Decks')}</h2><p role="status">{count(decks.length, 'deck')}</p></div>
      {decks.length ? <div className="explore-cards-grid">{decks.map(deck => <ExploreDeckCard key={deck.id} deck={deck} />)}</div>
        : <div className="explore-empty"><h3>{t('No public decks match your filters yet.')}</h3><p>{t('Try another search or clear your filters.')}</p><button className="button button-outline" type="button" onClick={() => setParams({})}>{t('Clear filters')}</button></div>}
    </section>
  </div>
}
