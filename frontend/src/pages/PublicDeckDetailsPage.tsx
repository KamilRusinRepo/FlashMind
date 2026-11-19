import { useEffect, useId, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ExploreApiError, getPublicDeck, getPublicFlashcards, type PublicExploreDeck } from '../api/explore'
import type { FlashcardPage } from '../api/flashcards'
import { useAuth } from '../auth/AuthContext'
import FlashcardPreview from '../components/flashcards/FlashcardPreview'
import FollowDeckButton, { type FollowState } from '../components/explore/FollowDeckButton'
import StudyStartButton from '../components/study/StudyStartButton'
import DeckReviews from '../components/reviews/DeckReviews'
import DeckRating from '../components/reviews/DeckRating'
import DeckDetailsTabs from '../components/decks/DeckDetailsTabs'
import { useLanguage } from '../i18n/LanguageContext'
import { categoryName, languageName } from '../i18n/labels'
import './MyDecks.css'
import './DeckDetails.css'
import './ExploreDecks.css'

type LoadState = { status: 'loading' } | { status: 'ready'; deck: PublicExploreDeck; cards: FlashcardPage }
  | { status: 'error'; message: string; code: number }

export default function PublicDeckDetailsPage() {
  const { t } = useLanguage()
  const { deckId } = useParams()
  const { search } = useLocation()
  const { user } = useAuth()
  const id = Number(deckId)
  const valid = /^\d+$/.test(deckId ?? '') && Number.isSafeInteger(id) && id > 0
  return <div className="db-page deck-details-page">
    <Link to={`/explore${search}`} className="back-link">{t('← Back to Explore Decks')}</Link>
    {valid ? <PublicDeckContent key={`${user?.id}:${id}`} deckId={id} /> : <Unavailable />}
  </div>
}

function PublicDeckContent({ deckId }: { deckId: number }) {
  const [page, setPage] = useState(0)
  return <PublicDeckLoader key={page} deckId={deckId} page={page} onPageChange={setPage} />
}

function PublicDeckLoader({ deckId, page, onPageChange }: { deckId: number; page: number; onPageChange: (page: number) => void }) {
  const tabsId = useId()
  const [activeTab, setActiveTab] = useState<'flashcards' | 'reviews'>('flashcards')
  const { t, count, locale } = useLanguage()
  const { refresh } = useAuth()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([getPublicDeck(deckId, controller.signal), getPublicFlashcards(deckId, page, controller.signal)]).then(([deck, cards]) => {
      if (controller.signal.aborted) return
      if (page > 0 && cards.content.length === 0) { onPageChange(Math.max(0, cards.totalPages - 1)); return }
      setState({ status: 'ready', deck, cards })
    }).catch(cause => {
      if (controller.signal.aborted) return
      if (cause instanceof ExploreApiError && cause.status === 401) refresh()
      setState({ status: 'error', code: cause instanceof ExploreApiError ? cause.status : 0,
        message: cause instanceof ExploreApiError ? cause.message : 'Unable to load this public deck. Please try again.' })
    })
    return () => controller.abort()
  }, [deckId, page, attempt, refresh, onPageChange])

  if (state.status === 'loading') return <p className="decks-message" role="status">{t('Loading public deck…')}</p>
  if (state.status === 'error') return state.code === 404 ? <Unavailable /> : <div className="decks-message">
    <p className="form-notice form-error" role="alert">{t(state.message)}</p>
    <button className="button button-outline" type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>{t('Try again')}</button>
  </div>

  const { deck, cards } = state
  const number = new Intl.NumberFormat(locale)
  return <>
    <PublicDeckHeader deck={deck} onChanged={next => setState({ status: 'ready', deck: { ...deck, ...next }, cards })} />
    <DeckDetailsTabs id={tabsId} activeTab={activeTab} onChange={setActiveTab} />
    <section className="deck-details-flashcards" role="tabpanel" tabIndex={0} id={`${tabsId}-flashcards-panel`} aria-labelledby={`${tabsId}-flashcards-tab`} hidden={activeTab !== 'flashcards'}>
      <div className="db-section-heading"><h2 id="public-flashcards-title">{t('Flashcards')}</h2><p role="status">{count(cards.totalElements, 'card')}</p></div>
      {cards.totalElements === 0 ? <div className="decks-empty"><h3>{t('No flashcards yet')}</h3><p>{t('This public deck has no flashcards yet.')}</p></div>
        : <div className="flashcard-preview-list">{cards.content.map((card, index) => <FlashcardPreview key={card.id} preview={false}
          index={page * cards.size + index} card={{ id: String(card.id), question: card.question, answer: card.answer,
            exampleSentence: card.exampleSentence ?? undefined, translatedExample: card.translatedExample ?? undefined, imageUrl: card.imageUrl ?? undefined }} />)}</div>}
      {cards.totalPages > 1 && <nav className="decks-pagination" aria-label={t('Flashcard pagination')}>
        <button className="button button-outline" type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>{t('← Previous')}</button>
        <span>{t('Page {0} of {1}', [number.format(page + 1), number.format(cards.totalPages)])}</span>
        <button className="button button-outline" type="button" disabled={page + 1 >= cards.totalPages} onClick={() => onPageChange(page + 1)}>{t('Next →')}</button>
      </nav>}
    </section>
    <div role="tabpanel" tabIndex={0} id={`${tabsId}-reviews-panel`} aria-labelledby={`${tabsId}-reviews-tab`} hidden={activeTab !== 'reviews'}>
      <DeckReviews deckId={deck.id} ownDeck={deck.ownDeck} />
    </div>
  </>
}

function PublicDeckHeader({ deck, onChanged }: { deck: PublicExploreDeck; onChanged: (next: FollowState) => void }) {
  const { t, count, locale } = useLanguage()
  const [failedImage, setFailedImage] = useState(false)
  const languageDeck = deck.deckType === 'LANGUAGE'
  const tone = languageDeck ? 'purple' : 'blue'
  return <div className="deck-details-overview">
    <header className="deck-details-hero">
      <div className={`deck-details-cover db-tone-${tone}`} aria-hidden="true">
        {deck.backgroundUrl && !failedImage ? <img src={deck.backgroundUrl} alt="" referrerPolicy="no-referrer" onError={() => setFailedImage(true)} />
          : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}
      </div>
      <div className="deck-details-heading">
        <div className="db-tags"><span>{t('PUBLIC')}</span><span className={`db-tone-${tone}`}>{deck.category ? categoryName(deck.category, t) : t('Uncategorized')}</span></div>
        <h1>{deck.name}</h1>
        <p>{t('By {0}', [deck.author.id === null ? t('Deleted user') : deck.author.displayName])}</p>
        {deck.description?.trim() && <p>{deck.description}</p>}
      </div>
    </header>
    <div className="deck-details-summary">
      <dl className="deck-details-meta">
        <div><dt>{t('Deck type')}</dt><dd>{languageDeck ? t('Language') : t('General')}</dd></div>
        <div><dt>{languageDeck ? t('Languages') : t('Language')}</dt><dd>{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> → {languageName(deck.targetLanguage, t)}</>}</dd></div>
        <div><dt>{t('Created')}</dt><dd><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'long', day: 'numeric' })}</time></dd></div>
      </dl>
      <p className="decks-result-count">{count(deck.cardCount, 'card')}</p>
      <p className="decks-result-count">{t('Followers: {0}', [deck.followerCount.toLocaleString(locale)])}</p>
      <DeckRating averageRating={deck.averageRating} reviewCount={deck.reviewCount} />
      <div className="deck-details-actions">
        {deck.ownDeck ? <Link className="button button-primary" to={`/decks/${deck.id}`}>{t('Manage Deck')}</Link>
          : <FollowDeckButton deckId={deck.id} following={deck.following} followerCount={deck.followerCount} onChanged={onChanged} />}
        {(deck.ownDeck || deck.following) && <StudyStartButton deckId={deck.id} disabled={deck.cardCount === 0} />}
      </div>
    </div>
  </div>
}

function Unavailable() {
  const { t } = useLanguage()
  return <section className="decks-empty"><h1>{t('Deck not found')}</h1><p>{t('This public deck is no longer available.')}</p></section>
}
