import type { RatingSummary } from '../../api/reviews'
import { useLanguage } from '../../i18n/LanguageContext'
import RatingStars from './RatingStars'

export default function DeckRating({ averageRating, reviewCount }: RatingSummary) {
  const { t, locale } = useLanguage()
  return <div className="deck-rating">{averageRating === null ? <span>{t('No ratings yet')}</span> : <>
    <RatingStars rating={averageRating} />
    <strong>{new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(averageRating)} / 5</strong>
    <span>{t('{0} reviews', [reviewCount.toLocaleString(locale)])}</span>
  </>}</div>
}
