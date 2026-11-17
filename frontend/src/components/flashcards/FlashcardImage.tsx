import { useLanguage } from '../../i18n/LanguageContext'
import { useState } from 'react'
import { isFlashcardImageUrl } from '../../data/flashcardPreview'

export default function FlashcardImage({ url }: { url?: string }) {
  const { t } = useLanguage()
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  if (!url || !isFlashcardImageUrl(url)) return null

  return <div className="flashcard-image">
    {failedUrl === url ? <p className="field-hint" role="status">{t("Unable to load this image. Check the image URL.")}</p>
      : <img src={url} alt={t("Flashcard illustration")} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedUrl(url)} />}
  </div>
}
