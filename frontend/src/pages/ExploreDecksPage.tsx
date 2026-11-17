import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useLanguage } from '../i18n/LanguageContext'
import { emptyExploreFilters, readExploreFilters, type ExploreFilters as Filters } from '../data/explore'
import { ExploreApiError, getPublicDecks, type PublicDeckPage } from '../api/explore'
import { useAuth } from '../auth/AuthContext'
import ExploreFilters from '../components/explore/ExploreFilters'
import ExploreDeckCard from '../components/explore/ExploreDeckCard'
import './ExploreDecks.css'

export default function ExploreDecksPage() {
  const { t } = useLanguage()
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const filters = readExploreFilters(params)
  const pageValue = params.get('page') ?? '0'
  const page = /^\d+$/.test(pageValue) && Number(pageValue) <= 1000000 ? Number(pageValue) : 0
  function parameters(next: Filters) {
    const query = new URLSearchParams()
    if (next.q.trim()) query.set('q', next.q.trim())
    if (next.deckType) query.set('deck_type', next.deckType)
    if (next.category) query.set('category', next.category)
    if (next.source) query.set('source_language', next.source)
    if (next.deckType === 'LANGUAGE' && next.target) query.set('target_language', next.target)
    if (next.sort !== 'newest') query.set('sort', next.sort)
    return query
  }
  const filterQuery = parameters(filters).toString()
  const goToPage = useCallback((nextPage: number) => {
    const next = new URLSearchParams(filterQuery)
    if (nextPage > 0) next.set('page', String(nextPage))
    setParams(next)
  }, [filterQuery, setParams])
  const query = new URLSearchParams(filterQuery)
  if (page > 0) query.set('page', String(page))
  function apply(next: Filters) { setParams(parameters(next)) }

  return <div className="explore-page">
    <header className="explore-heading"><h1>{t('Explore Marketplace')}</h1><p>{t('Find public decks created by other learners.')}</p></header>
    <p className="explore-preview">{t('Follow public decks to add them to your library and study them.')}</p>
    <ExploreFilters key={filterQuery} initial={filters} onApply={apply} />
    <ExploreResults key={`${user?.id}:${query}`} query={query.toString()} page={page} goToPage={goToPage} onClear={() => apply(emptyExploreFilters)} />
  </div>
}

type ResultsState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: PublicDeckPage }
function ExploreResults({ query, page, goToPage, onClear }: { query: string; page: number; goToPage: (page: number) => void; onClear: () => void }) {
  const { t, count, locale } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<ResultsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getPublicDecks(query, page, controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ status: 'ready', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof ExploreApiError && cause.status === 401) refresh()
      setState({ status: 'error', message: cause instanceof ExploreApiError ? cause.message : 'Unable to load public decks. Please try again.' })
    })
    return () => controller.abort()
  }, [query, page, attempt, refresh])
  if (state.status === 'loading') return <p className="explore-empty" role="status">{t('Loading public decks…')}</p>
  if (state.status === 'error') return <div className="explore-empty"><p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button></div>
  const { data } = state
  const number = new Intl.NumberFormat(locale)
  return (
    <section aria-labelledby="explore-trending-title">
      <div className="explore-results-heading"><h2 id="explore-trending-title">{t('Public Decks')}</h2><p role="status">{count(data.totalElements, 'deck')}</p></div>
      {data.content.length ? <div className="explore-cards-grid">{data.content.map(deck => <ExploreDeckCard key={deck.id} deck={deck} />)}</div>
        : data.totalElements > 0 ? <div className="explore-empty"><p>{t('No decks on this page.')}</p><button className="button button-outline" type="button" onClick={() => goToPage(0)}>{t('Go to first page')}</button></div>
        : <div className="explore-empty"><h3>{t('No public decks match your filters yet.')}</h3><p>{t('Try another search or clear your filters.')}</p><button className="button button-outline" type="button" onClick={onClear}>{t('Clear filters')}</button></div>}
      {data.totalPages > 1 && page < data.totalPages && <nav className="explore-pagination" aria-label={t('Public deck pages')}>
        <button className="button button-outline" type="button" disabled={page === 0} onClick={() => goToPage(page - 1)}>{t('Previous')}</button>
        <span>{t('Page {0} of {1}', [number.format(page + 1), number.format(data.totalPages)])}</span>
        <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>{t('Next')}</button>
      </nav>}
    </section>
  )
}
