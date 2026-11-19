import { useLanguage } from '../../i18n/LanguageContext'
import '../../pages/PublicDeckDetails.css'

export type DeckDetailsTab = 'flashcards' | 'reviews'

export default function DeckDetailsTabs({ id, activeTab, onChange }: {
  id: string; activeTab: DeckDetailsTab; onChange: (tab: DeckDetailsTab) => void
}) {
  const { t } = useLanguage()
  return <div className="public-deck-tabs" role="tablist" aria-label={t('Deck sections')}>
    {(['flashcards', 'reviews'] as const).map((tab, index) => <button key={tab} id={`${id}-${tab}-tab`} type="button" role="tab"
      aria-selected={activeTab === tab} aria-controls={`${id}-${tab}-panel`} tabIndex={activeTab === tab ? 0 : -1}
      onClick={() => onChange(tab)} onKeyDown={event => {
        const next = event.key === 'Home' ? 'flashcards' : event.key === 'End' ? 'reviews'
          : event.key === 'ArrowLeft' || event.key === 'ArrowRight' ? (index === 0 ? 'reviews' : 'flashcards') : null
        if (next) { event.preventDefault(); onChange(next); document.getElementById(`${id}-${next}-tab`)?.focus() }
      }}>{t(tab === 'flashcards' ? 'Flashcards' : 'Reviews')}</button>)}
  </div>
}
