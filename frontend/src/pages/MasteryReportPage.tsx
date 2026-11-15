import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useLanguage } from '../i18n/LanguageContext'
import { sampleDeckMastery } from '../data/statistics'
import { createSampleMasteryCards, masteryRatings } from '../data/mastery'
import MasteryStars from '../components/statistics/MasteryStars'
import './MasteryReport.css'

export default function MasteryReportPage() {
  const { reportDeckId } = useParams()
  const deck = sampleDeckMastery.find(item => item.slug === reportDeckId)
  const { t } = useLanguage()
  if (!deck) return <div className="mastery-report-page"><h1>{t('Sample report not found')}</h1><Link className="button button-outline" to="/statistics/decks">{t('Back to deck statistics')}</Link></div>
  return <MasteryReport key={deck.slug} deck={deck} />
}

function MasteryReport({ deck }: { deck: typeof sampleDeckMastery[number] }) {
  const { t, locale } = useLanguage()
  const [sort, setSort] = useState('strongest')
  const [stars, setStars] = useState(0)
  const [today] = useState(() => new Date())
  const cards = createSampleMasteryCards(deck)
  const visible = cards.filter(card => !stars || card.level === stars).sort((a, b) =>
    (sort === 'strongest' ? b.level - a.level : a.level - b.level) || a.question.localeCompare(b.question, locale))
  const number = new Intl.NumberFormat(locale)
  const relative = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const date = new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' })
  const nextDate = (days: number) => date.format(new Date(today.getFullYear(), today.getMonth(), today.getDate() + days))

  return <div className="mastery-report-page">
    <header className="mastery-report-header">
      <div><p className="mastery-report-kicker">{t('Mastery Report')}</p><h1>{deck.name}</h1><p>{t('{0} cards in this deck. Review cards marked Bad or Average to improve retention.', [number.format(deck.total)])}</p></div>
      <div className="mastery-report-actions">
        <button className="button button-outline" type="button" disabled title={t('Available after backend integration')}>{t('Export')}</button>
        <button className="button button-primary" type="button" disabled title={t('Available after backend integration')}>{t('Study Weak Cards')}</button>
      </div>
    </header>
    <div className="mastery-report-notice"><p className="mastery-report-preview">{t('Sample data only. Your learning activity is not connected yet.')}</p><Link to="/statistics/decks">← {t('Back to deck statistics')}</Link></div>
    <div className="mastery-report-buckets">{masteryRatings.map(rating => <section key={rating.level} className={`mastery-report-bucket mastery-tone-${rating.tone}`}>
      <MasteryStars level={rating.level} /><strong>{number.format(cards.filter(card => card.level === rating.level).length)}</strong>
      <h2>{t('Cards: {0}', [t(rating.label)])}</h2><img src={rating.icon} alt="" aria-hidden="true" />
    </section>)}</div>
    <section aria-labelledby="individual-card-mastery">
      <div className="mastery-report-table-heading"><h2 id="individual-card-mastery">{t('Individual Card Mastery')}</h2>
        <div className="mastery-report-filters">
          <label><span>{t('Sort')}</span><select value={sort} onChange={event => setSort(event.target.value)}>
            <option value="strongest">{t('Best known first')}</option><option value="weakest">{t('Weakest first')}</option>
          </select></label>
          <label><span>{t('Stars')}</span><select value={stars} onChange={event => setStars(Number(event.target.value))}>
            <option value={0}>{t('All stars')}</option><option value={1}>{t('1 star')}</option><option value={2}>{t('2 stars')}</option><option value={3}>{t('3 stars')}</option><option value={4}>{t('4 stars')}</option>
          </select></label>
        </div>
      </div>
      <div className="mastery-report-table-head" aria-hidden="true"><span>{t('Card')}</span><span>{t('Mastery Level')}</span><span>{t('Last Reviewed')}</span></div>
      <div className="mastery-report-list">
        {visible.length === 0 && <p className="mastery-report-empty" role="status">{t('No cards match this star rating.')}</p>}
        {visible.map(card => {
          const rating = masteryRatings[card.level - 1]
          return <article className={`mastery-report-row mastery-tone-${rating.tone}`} key={card.id}>
            <div className="mastery-report-front"><span className="mastery-report-face"><img src={rating.icon} alt="" /></span><div><h3>{card.question}</h3><p>{card.answer}</p></div></div>
            <div className="mastery-report-level"><MasteryStars level={card.level} /><span>{t(rating.label)}</span></div>
            <dl className="mastery-report-dates"><div><dt className="db-sr-only">{t('Last Reviewed')}</dt><dd>{card.lastReviewedDaysAgo === null ? t('Never') : relative.format(-card.lastReviewedDaysAgo, 'day')}</dd></div>
              <div><dt>{t('Next:')}</dt><dd>{card.nextReviewInDays === null ? t('Ready now') : nextDate(card.nextReviewInDays)}</dd></div>
            </dl>
          </article>
        })}
      </div>
    </section>
    <p className="mastery-report-preview">{t('Export and weak-card study will be available after backend integration.')}</p>
  </div>
}
