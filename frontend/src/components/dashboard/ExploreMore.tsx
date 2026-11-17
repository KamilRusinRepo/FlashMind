import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ExploreApiError, getPublicDecks, type PublicExploreDeck } from '../../api/explore'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import ExploreDeckCard from '../explore/ExploreDeckCard'
import '../../pages/ExploreDecks.css'

type LoadState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; decks: PublicExploreDeck[] }

export default function ExploreMore() {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getPublicDecks('sort=followers', 0, controller.signal).then(page => {
      if (!controller.signal.aborted) setState({ status: 'ready', decks: page.content.slice(0, 3) })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof ExploreApiError && cause.status === 401) refresh()
      setState({ status: 'error', message: cause instanceof ExploreApiError ? cause.message : 'Unable to load public decks. Please try again.' })
    })
    return () => controller.abort()
  }, [attempt, refresh])

  return <section className="db-section db-explore-section" aria-labelledby="explore-title">
    <div className="db-section-heading">
      <div><h2 id="explore-title">{t('Explore More')}</h2><p>{t('Find public decks created by other learners.')}</p></div>
      <Link className="db-text-action" to="/explore?sort=followers">{t('Explore Marketplace')} <span aria-hidden="true">→</span></Link>
    </div>
    {state.status === 'loading' ? <p className="decks-message" role="status">{t('Loading public decks…')}</p>
      : state.status === 'error' ? <div className="decks-message">
        <p className="form-notice form-error" role="alert">{t(state.message)}</p>
        <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button>
      </div>
        : state.decks.length === 0 ? <div className="decks-empty"><h3>{t('No public decks yet.')}</h3></div>
          : <div className="explore-cards-grid">{state.decks.map(deck => <ExploreDeckCard key={deck.id} deck={deck} />)}</div>}
  </section>
}
