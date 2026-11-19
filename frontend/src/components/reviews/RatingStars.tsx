import { useLanguage } from '../../i18n/LanguageContext'
import './RatingStars.css'

export function StarIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m12 2.5 2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.3l-5.8 3.1 1.1-6.5-4.7-4.6 6.5-.9Z" /></svg>
}

export default function RatingStars({ rating }: { rating: number }) {
  const { t, locale } = useLanguage()
  return <span className="review-stars" role="img" aria-label={t('Rated {0} out of 5', [new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(rating)])}>
    {[1, 2, 3, 4, 5].map(star => <span className="review-star" key={star}>
      <StarIcon /><span className="review-star-fill" style={{ width: `${Math.max(0, Math.min(1, rating - star + 1)) * 100}%` }}><StarIcon /></span>
    </span>)}
  </span>
}
