import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useLanguage } from '../i18n/LanguageContext'
import { masteryRatings, type MasteryLevel } from '../data/mastery'
import { isMasteryReport } from '../api/statistics'
import { useStatisticsResource } from '../components/statistics/useStatisticsResource'
import StatisticsStatus from '../components/statistics/StatisticsStatus'
import MasteryStars from '../components/statistics/MasteryStars'
import './MasteryReport.css'

export default function MasteryReportPage() {
  const { reportDeckId } = useParams()
  const deckId = Number(reportDeckId)
  const { t } = useLanguage()
  if (!Number.isSafeInteger(deckId) || deckId < 1) return <div className="mastery-report-page"><h1>{t('This deck is no longer available.')}</h1><Link className="button button-outline" to="/statistics/decks">{t('Back to deck statistics')}</Link></div>
  return <MasteryReport key={deckId} deckId={deckId} />
}

function MasteryReport({ deckId }: { deckId: number }) {
  const { t, locale } = useLanguage()
  const [sort, setSort] = useState('strongest')
  const [stars, setStars] = useState(0)
  const [page, setPage] = useState(0)
  const params = new URLSearchParams({ stars: String(stars), sort, page: String(page), size: '20' })
  const { state, retry } = useStatisticsResource(`/api/statistics/me/decks/${deckId}?${params}`, isMasteryReport)
  if (state.status !== 'ready') return <div className="mastery-report-page"><Link to="/statistics/decks">← {t('Back to deck statistics')}</Link><StatisticsStatus message={state.status === 'error' ? state.message : undefined} retry={retry} /></div>
  const deck = state.data
  if (deck.id !== deckId || deck.page !== page || deck.size !== 20) return <StatisticsStatus message="The server returned unexpected statistics. Please try again." retry={retry} />
  const visible = deck.content
  const number = new Intl.NumberFormat(locale)
  const relative = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const date = new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric', timeZone: deck.zone })
  function reviewedAt(value: string) {
    const parts = new Intl.DateTimeFormat('en', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: deck.zone }).formatToParts(new Date(value))
    const part = (name: string) => parts.find(item => item.type === name)?.value
    const day = `${part('year')}-${part('month')}-${part('day')}`
    const elapsed = Math.round((Date.parse(deck.today) - Date.parse(day)) / 86400000)
    return relative.format(-elapsed, 'day')
  }

  return <div className="mastery-report-page">
    <header className="mastery-report-header">
      <div><p className="mastery-report-kicker">{t('Mastery Report')}</p><h1>{deck.name}</h1><p>{t('{0} cards in this deck. Review cards marked Bad or Average to improve retention.', [number.format(deck.total)])}</p></div>
      <div className="mastery-report-actions">
        <button className="button button-outline" type="button" disabled title={t('This feature is coming soon.')}>{t('Export')}</button>
        <button className="button button-primary" type="button" disabled title={t('This feature is coming soon.')}>{t('Study Weak Cards')}</button>
      </div>
    </header>
    <div className="mastery-report-notice"><p className="mastery-report-preview">{t('Unstudied cards appear in the one-star group. Mastery changes during Scheduled Review.')}</p><Link to="/statistics/decks">← {t('Back to deck statistics')}</Link></div>
    <div className="mastery-report-buckets">{masteryRatings.map(rating => <section key={rating.level} className={`mastery-report-bucket mastery-tone-${rating.tone}`}>
      <MasteryStars level={rating.level} /><strong>{number.format(deck.buckets.find(bucket => bucket.level === rating.level)?.count ?? 0)}</strong>
      <h2>{t('Cards: {0}', [t(rating.label)])}</h2><img src={rating.icon} alt="" aria-hidden="true" />
    </section>)}</div>
    <section aria-labelledby="individual-card-mastery">
      <div className="mastery-report-table-heading"><h2 id="individual-card-mastery">{t('Individual Card Mastery')}</h2>
        <div className="mastery-report-filters">
          <label><span>{t('Sort')}</span><select value={sort} onChange={event => { setSort(event.target.value); setPage(0) }}>
            <option value="strongest">{t('Best known first')}</option><option value="weakest">{t('Weakest first')}</option>
          </select></label>
          <label><span>{t('Stars')}</span><select value={stars} onChange={event => { setStars(Number(event.target.value)); setPage(0) }}>
            <option value={0}>{t('All stars')}</option><option value={1}>{t('1 star')}</option><option value={2}>{t('2 stars')}</option><option value={3}>{t('3 stars')}</option><option value={4}>{t('4 stars')}</option>
          </select></label>
        </div>
      </div>
      <div className="mastery-report-table-head" aria-hidden="true"><span>{t('Card')}</span><span>{t('Mastery Level')}</span><span>{t('Last Reviewed')}</span></div>
      <div className="mastery-report-list">
        {visible.length === 0 && <p className="mastery-report-empty" role="status">{deck.total === 0 ? t('No cards in this deck yet.') : t('No cards match this star rating.')}</p>}
        {visible.map(card => {
          const level = Math.max(1, card.masteryLevel) as MasteryLevel
          const rating = masteryRatings[level - 1]
          return <article className={`mastery-report-row mastery-tone-${rating.tone}`} key={card.id}>
            <div className="mastery-report-front"><span className="mastery-report-face"><img src={rating.icon} alt="" /></span><div><h3>{card.question}</h3><p>{card.answer}</p></div></div>
            <div className="mastery-report-level"><MasteryStars level={level} /><span>{card.masteryLevel === 0 ? t('Not studied yet') : t(rating.label)}</span></div>
            <dl className="mastery-report-dates"><div><dt className="db-sr-only">{t('Last Reviewed')}</dt><dd>{card.lastReviewedAt === null ? t('Never') : reviewedAt(card.lastReviewedAt)}</dd></div>
              <div><dt>{t('Next:')}</dt><dd>{card.nextReviewAt === null ? t('Ready now') : date.format(new Date(card.nextReviewAt))}</dd></div>
            </dl>
          </article>
        })}
      </div>
      {deck.totalPages > 1 || page > 0 ? <nav className="mastery-report-pagination" aria-label={t('Card pages')}>
        <button className="button button-outline" type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>{t('Previous')}</button>
        <span>{t('Page {0} of {1}', [page + 1, Math.max(1, deck.totalPages)])}</span>
        <button className="button button-outline" type="button" disabled={page + 1 >= deck.totalPages} onClick={() => setPage(value => value + 1)}>{t('Next')}</button>
      </nav> : null}
    </section>
    <p className="mastery-report-preview">{t('Export and weak-card study are coming soon.')}</p>
  </div>
}
