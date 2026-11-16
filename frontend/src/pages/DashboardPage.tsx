import { useLanguage } from '../i18n/LanguageContext'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import DashboardStats from '../components/dashboard/DashboardStats'
import ExploreDeckCard from '../components/dashboard/DeckCard'
import ContinueLearning from '../components/dashboard/ContinueLearning'
import DashboardDeckCard from '../components/dashboard/DashboardDeckCard'
import { exploreDecks } from '../data/dashboard'
import { DeckApiError, getMyDecks, type DeckPage } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import { isDeckMasteryList } from '../api/statistics'
import { useStatisticsResource } from '../components/statistics/useStatisticsResource'
import './MyDecks.css'

type DeckState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; page: DeckPage }

export default function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null
  return <DashboardContent key={user.id} username={user.username} />
}

function DashboardContent({ username }: { username: string }) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const learning = useStatisticsResource('/api/statistics/me/decks', isDeckMasteryList)
  const [state, setState] = useState<DeckState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getMyDecks(0, '', controller.signal).then(page => {
      if (!controller.signal.aborted) setState({ status: 'ready', page })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof DeckApiError && cause.status === 401) { refresh(); return }
      setState({ status: 'error', message: cause instanceof DeckApiError ? cause.message : 'Unable to load your decks. Please try again.' })
    })
    return () => controller.abort()
  }, [attempt, refresh])

  function reload() {
    setState({ status: 'loading' })
    setAttempt(value => value + 1)
  }

  const decks = state.status === 'ready' ? state.page.content : []
  const total = state.status === 'ready' ? state.page.totalElements : 0
  const masteryDecks = learning.state.status === 'ready' ? learning.state.data : []

  return <div className="db-page">
    <header className="db-hero">
      <div className="db-welcome"><p className="db-eyebrow">{t("A LITTLE PRACTICE. A LASTING MEMORY.")}</p>
        <h1>{t("Welcome back,")} {username}!</h1>
        <p>{t("A little practice today. A little more confidence tomorrow.")}</p>
      </div>
      <DashboardStats />
    </header>
    <section className="db-section" aria-labelledby="continue-title">
      <div className="db-section-heading"><div><h2 id="continue-title">{t("Continue Learning")}</h2><p>{t("Your latest decks, ready for your next step.")}</p></div>
        <Link to="/decks" className="db-text-action">{t("View All Decks")} <span aria-hidden="true">→</span></Link>
      </div>
      <ContinueLearning state={learning.state} retry={learning.retry} />
    </section>
    <section className="db-section" aria-labelledby="my-decks-title">
      <div className="db-section-heading"><h2 id="my-decks-title">{t("My Decks")}</h2><div className="db-heading-actions">
        <Link className="db-text-action" to="/decks">{t("View all")}{state.status === 'ready' ? ` (${total})` : ''}</Link>
        <Link className="db-create-action" to="/decks/create"><span aria-hidden="true">＋</span> {t("Create Deck")}</Link>
      </div></div>
      {state.status === 'loading' ? <p className="decks-message" role="status">{t("Loading your decks…")}</p>
        : state.status === 'error' ? <div className="decks-message"><p className="form-notice form-error" role="alert">{t(state.message)}</p>
          <button className="button button-outline" type="button" onClick={() => reload()}>{t("Try again")}</button></div>
        : decks.length === 0 ? <div className="decks-empty"><h3>{t("No decks yet")}</h3><p>{t("Create a deck and make a little room for learning.")}</p>
          <Link className="button button-primary" to="/decks/create">{t("Create your first deck")}</Link></div>
        : <>
          {learning.state.status === 'error' && <div className="db-dashboard-notice"><p role="alert">{t(learning.state.message)}</p><button className="db-text-action" type="button" onClick={learning.retry}>{t('Try again')}</button></div>}
          <div className="db-deck-grid">{decks.slice(0, 3).map(deck => <DashboardDeckCard key={deck.id} deck={deck}
            mastery={masteryDecks.find(item => item.id === deck.id)} masteryLoading={learning.state.status === 'loading'} />)}</div>
        </>}
    </section>
    <section className="db-section" aria-labelledby="explore-title">
      <div className="db-section-heading"><div><h2 id="explore-title">{t("Explore More")}</h2><p>{t("Sample public decks. Explore is coming soon.")}</p></div><button className="db-text-action" type="button" disabled title={t("Exploring public decks is coming soon")}>{t("Explore Marketplace")} <span aria-hidden="true">→</span></button></div>
      <div className="db-deck-grid">{exploreDecks.map(deck => <ExploreDeckCard key={deck.id} deck={deck} />)}</div>
    </section>
    <footer className="db-page-footer">{t("Small steps. Big progress.")}<span>{t("Your decks. Your pace.")}</span></footer>
  </div>
}
