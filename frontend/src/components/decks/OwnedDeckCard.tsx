import { useLanguage } from '../../i18n/LanguageContext'
import { categoryName, languageName } from '../../i18n/labels'
import type { OwnedDeck } from '../../api/decks'
import { useState } from 'react'
import { Link } from 'react-router'
import DeleteDeckDialog from './DeleteDeckDialog'
import FollowDeckButton from '../explore/FollowDeckButton'
import StudyStartButton from '../study/StudyStartButton'
import '../../pages/ExploreDecks.css'

type Props = { deck: OwnedDeck } & (
  { showActions: true; onChanged: (deleted: boolean) => void }
  | { showActions?: false; onChanged?: never }
)

export default function OwnedDeckCard({ deck, showActions = false, onChanged }: Props) {
  const { t, locale } = useLanguage()
  const tone = deck.deckType === 'LANGUAGE' ? 'purple' : 'blue'
  const [failedImage, setFailedImage] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  return <><article className="db-deck-card decks-list-card">
    <div className={`db-deck-cover db-tone-${tone}`} aria-hidden="true">{deck.backgroundUrl && failedImage !== deck.backgroundUrl
      ? <img src={deck.backgroundUrl} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedImage(deck.backgroundUrl)} />
      : <span>{deck.name.slice(0, 1).toUpperCase()}</span>}</div>
    <div className="db-deck-body">
      <div className="db-tags"><span>{deck.publicDeck ? t("PUBLIC") : t("PRIVATE")}</span><span className={`db-tone-${tone}`}>{deck.category ? categoryName(deck.category, t) : t("Uncategorized")}</span></div>
      <h3><Link className="deck-details-link" to={deck.ownDeck ? `/decks/${deck.id}` : `/explore/${deck.id}`} aria-label={t("Open {0}", [deck.name])}>{deck.name}</Link></h3>
      {!deck.ownDeck && <p className="db-deck-description">{t('Following')} · {t('By {0}', [deck.author.id === null ? t('Deleted user') : deck.author.displayName])}</p>}
      <p className="db-deck-description">{deck.description || t("No description yet.")}</p>
      <div className="decks-language"><span>{deck.deckType === 'LANGUAGE' ? t("Language deck") : t("General deck")}</span>
        <strong>{languageName(deck.sourceLanguage, t)}{deck.targetLanguage && <> <span aria-hidden="true">→</span> {languageName(deck.targetLanguage, t)}</>}</strong>
      </div>
      <div className="db-deck-footer"><span>{t("Created")}</span><time dateTime={deck.createdAt}>{new Date(deck.createdAt).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })}</time></div>
      {showActions && deck.ownDeck && <div className="deck-card-actions">
        <Link className="button button-outline" to={`/decks/${deck.id}/edit`} aria-label={t("Edit {0}", [deck.name])}>{t("Edit")}</Link>
        <button className="button deck-delete-button" type="button" onClick={() => setConfirmingDelete(true)} aria-label={t("Delete {0}", [deck.name])}>{t("Delete")}</button>
      </div>}
      {showActions && !deck.ownDeck && <div className="deck-card-actions">
        <StudyStartButton deckId={deck.id} />
        <FollowDeckButton deckId={deck.id} following onChanged={() => onChanged?.(false)} />
      </div>}
    </div>
  </article>{showActions && deck.ownDeck && confirmingDelete && <DeleteDeckDialog deck={deck} onCancel={() => setConfirmingDelete(false)}
    onDeleted={() => { setConfirmingDelete(false); onChanged?.(true) }}
    onUnavailable={() => { setConfirmingDelete(false); onChanged?.(false) }} />}</>
}
