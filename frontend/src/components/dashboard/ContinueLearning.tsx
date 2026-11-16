import { Link } from 'react-router'
import { useLanguage } from '../../i18n/LanguageContext'
import type { DeckMastery } from '../../api/statistics'
import type { StatisticsResource } from '../statistics/useStatisticsResource'
import { LearningCard } from './DeckCard'

export default function ContinueLearning({ state, retry }: { state: StatisticsResource<DeckMastery[]>; retry: () => void }) {
  const { t } = useLanguage()
  if (state.status === 'loading') return <p className="db-section-placeholder" role="status">{t('Loading your decks…')}</p>
  if (state.status === 'error') return <div className="db-section-placeholder"><p role="alert">{t(state.message)}</p><button className="db-text-action" type="button" onClick={retry}>{t('Try again')}</button></div>
  if (state.data.length === 0) return <p className="db-section-placeholder"><Link to="/decks/create">{t('Create your first deck to get started.')}</Link></p>
  const decks = state.data.toSorted((a, b) => b.dueCards - a.dueCards).slice(0, 3)
  return <div className="db-learning-grid">{decks.map(deck => <LearningCard key={deck.id} deck={deck} />)}</div>
}
