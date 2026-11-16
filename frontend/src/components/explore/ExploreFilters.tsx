import { useState, type FormEvent } from 'react'
import { useLanguage } from '../../i18n/LanguageContext'
import { categoryName, languageName } from '../../i18n/labels'
import { emptyExploreFilters, exploreCategories, exploreLanguages, exploreSorts, type ExploreFilters as Filters } from '../../data/explore'
import FormField from '../FormField'
import SelectField from '../SelectField'

export default function ExploreFilters({ initial, onApply }: { initial: Filters; onApply: (filters: Filters) => void }) {
  const { t } = useLanguage()
  const [draft, setDraft] = useState(initial)
  const change = (key: keyof Filters, value: string) => setDraft(current => ({ ...current, [key]: value }))
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); onApply({ ...draft, q: draft.q.trim() }) }
  function clear() { setDraft(emptyExploreFilters); onApply(emptyExploreFilters) }

  return <form className={`explore-filters${draft.deckType === 'LANGUAGE' ? ' has-target-language' : ''}`} role="search" onSubmit={submit}>
    <div className="explore-search-field"><FormField id="explore-search" label={t('Search')} type="search" maxLength={120}
      placeholder={t('Search decks, subjects, or creators…')} value={draft.q} onChange={event => change('q', event.target.value)} /></div>
    <SelectField id="explore-type" label={t('Deck Type')} value={draft.deckType} onChange={event => {
      const value = event.target.value
      setDraft(current => ({ ...current, deckType: value, target: value === 'LANGUAGE' ? current.target : '' }))
    }}><option value="">{t('All Types')}</option><option value="GENERAL">{t('General')}</option><option value="LANGUAGE">{t('Language')}</option></SelectField>
    <SelectField id="explore-category" label={t('Category')} value={draft.category} onChange={event => change('category', event.target.value)}>
      <option value="">{t('All Categories')}</option>{exploreCategories.map(category => <option value={category} key={category}>{categoryName(category, t)}</option>)}
    </SelectField>
    <SelectField id="explore-source" label={t('Source Language')} value={draft.source} onChange={event => change('source', event.target.value)}>
      <option value="">{t('Any Language')}</option>{exploreLanguages.map(language => <option value={language} key={language}>{languageName(language, t)}</option>)}
    </SelectField>
    {draft.deckType === 'LANGUAGE' && <SelectField id="explore-target" label={t('Target Language')} value={draft.target} onChange={event => change('target', event.target.value)}>
      <option value="">{t('Any Language')}</option>{exploreLanguages.map(language => <option value={language} key={language}>{languageName(language, t)}</option>)}
    </SelectField>}
    <SelectField id="explore-sort" label={t('Sort By')} value={draft.sort} onChange={event => change('sort', event.target.value)}>
      {exploreSorts.map(sort => <option value={sort.value} key={sort.value}>{t(sort.label)}</option>)}
    </SelectField>
    <div className="explore-filter-actions"><button className="button button-outline" type="button" onClick={clear}>{t('Clear filters')}</button><button className="button button-primary" type="submit">{t('Search')}</button></div>
  </form>
}
