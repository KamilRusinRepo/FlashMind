import { useLanguage } from '../i18n/LanguageContext'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { DeckApiError, getMyDecks, getFollowedDecks, type DeckPage } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import OwnedDeckCard from '../components/decks/OwnedDeckCard'
import './MyDecks.css'

export default function MyDecksPage() {
  const { t } = useLanguage()
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()
  const { state } = useLocation()
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const successNotice = notice ?? (typeof state?.deletedDeckId === 'number' ? 'Your deck has been deleted.'
    : typeof state?.updatedDeckId === 'number' ? 'Your deck has been updated.'
    : typeof state?.createdDeckId === 'number' ? 'Your deck has been created.' : '')
  const search = (params.get('search') ?? '').trim().slice(0, 120)
  const pageValue = params.get('page') ?? '0'
  const page = /^\d+$/.test(pageValue) && Number(pageValue) <= 1000000 ? Number(pageValue) : 0
  const followingPageValue = params.get('following_page') ?? '0'
  const followingPage = /^\d+$/.test(followingPageValue) && Number(followingPageValue) <= 1000000 ? Number(followingPageValue) : 0

  const goToPage = useCallback((nextPage: number) => {
    setParams(current => {
      const next = new URLSearchParams(current)
      if (nextPage > 0) next.set('page', String(nextPage)); else next.delete('page')
      return next
    })
  }, [setParams])

  const goToFollowingPage = useCallback((nextPage: number) => {
    setParams(current => {
      const next = new URLSearchParams(current)
      if (nextPage > 0) next.set('following_page', String(nextPage)); else next.delete('following_page')
      return next
    })
  }, [setParams])

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('search') ?? '').trim()
    setParams(value ? { search: value } : {})
  }

  return <div className="db-page">
    {successNotice && <p className="form-notice form-success" role="status">{t(successNotice)}</p>}
    <header className="db-section-heading decks-heading"><div><p className="db-eyebrow">{t("YOUR PERSONAL LIBRARY")}</p><h1>{t("My Decks")}</h1>
      <p>{t("Your ideas, organized for your next learning session.")}</p></div>
      <Link className="db-create-action" to="/decks/create">{t("＋ Create Deck")}</Link>
    </header>
    <form key={search} className="decks-search" role="search" onSubmit={handleSearch}>
      <FormField id="deck-search" name="search" label={t("Search your decks")} type="search" defaultValue={search} maxLength={120} placeholder={t("Search by deck name…")} />
      <button className="button button-primary" type="submit">{t("Search")}</button>
      {search && <button className="button button-outline" type="button" onClick={() => setParams({})}>{t("Clear")}</button>}
    </form>
    <section className="decks-library-section" aria-labelledby="owned-decks-title">
      <div className="db-section-heading"><h2 id="owned-decks-title">{t('Your decks')}</h2></div>
      <DeckResults key={JSON.stringify([user?.id, page, search, revision])} page={page} search={search} goToPage={goToPage}
        onChanged={deleted => { setNotice(deleted ? 'Your deck has been deleted.' : ''); setRevision(value => value + 1) }} />
    </section>
    <section className="decks-library-section" aria-labelledby="following-decks-title">
      <div className="db-section-heading"><h2 id="following-decks-title">{t('Following Decks')}</h2><Link className="db-text-action" to="/explore">{t('Explore More')} →</Link></div>
      <DeckResults key={JSON.stringify([user?.id, followingPage, search])} following page={followingPage} search={search} goToPage={goToFollowingPage} />
    </section>
  </div>
}

type ResultsState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'success'; data: DeckPage }

function DeckResults({ page, search, goToPage, onChanged, following = false }: { page: number; search: string; goToPage: (page: number) => void; onChanged?: (deleted: boolean) => void; following?: boolean }) {
  const { t, count } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<ResultsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const load = following ? getFollowedDecks : getMyDecks
    load(page, search, controller.signal).then(data => {
      if (controller.signal.aborted) return
      if (data.content.length === 0 && page > 0) { goToPage(Math.max(0, Math.min(page - 1, data.totalPages - 1))); return }
      setState({ status: 'success', data })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) {
        refresh()
        return
      }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load your decks. Please try again.' })
    })
    return () => controller.abort()
  }, [page, search, attempt, refresh, goToPage, following])

  if (state.status === 'loading') return <p className="decks-message" role="status">{following ? t('Loading followed decks…') : t("Loading your decks…")}</p>
  if (state.status === 'error') return <div className="decks-message"><p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t("Try again")}</button></div>

  const { data } = state
  const emptyTitleId = following ? 'empty-following-decks-title' : 'empty-owned-decks-title'
  if (data.content.length === 0) return <section className="decks-empty" aria-labelledby={emptyTitleId}>
    <span className="decks-empty-icon" aria-hidden="true">▤</span>
    <h3 id={emptyTitleId}>{page > 0 ? t("This page has no decks") : search ? t("No matching decks") : following ? t('No followed decks yet') : t("No decks yet")}</h3>
    <p>{page > 0 ? t("Return to the first page to see the current results.") : search ? t("Try another name or clear your search.") : following ? t('Follow public decks in Explore to see them here.') : t("Your decks will appear here once you create them.")}</p>
    {page > 0 && <button className="button button-outline" type="button" onClick={() => goToPage(0)}>{t("Go to first page")}</button>}
  </section>

  return <section aria-label={following ? t('Following Decks') : t("Your decks")}>
    <p className="decks-result-count" role="status">{count(data.totalElements, 'deck')}{search && t(" matching “{0}”", [search])}</p>
    <div className="db-deck-grid">{data.content.map(deck => following ? <OwnedDeckCard key={deck.id} deck={deck} />
      : <OwnedDeckCard key={deck.id} deck={deck} showActions onChanged={deleted => onChanged?.(deleted)} />)}</div>
    {data.totalPages > 1 && <nav className="decks-pagination" aria-label={following ? t('Following deck pagination') : t("Deck pagination")}>
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => goToPage(page - 1)}>{t("← Previous")}</button>
      <span>{t("Page")} {page + 1} {t("of")} {data.totalPages}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>{t("Next →")}</button>
    </nav>}
  </section>
}
