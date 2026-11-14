import { useLanguage } from '../i18n/LanguageContext'
import { Link } from 'react-router'

export default function NotFoundPage() {
  const { t } = useLanguage()
  return (
    <section className="not-found">
      <span className="eyebrow">404</span>
      <h1>{t("A little off track.")}</h1>
      <p>{t("We couldn’t find this page. Let’s get you back to the start.")}</p>
      <Link className="button button-primary" to="/">{t("Back to home")}</Link>
    </section>
  )
}
