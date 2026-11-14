import { useLanguage } from '../i18n/LanguageContext'
import { Link } from 'react-router'
import logo from '../assets/icons/logo.svg'

export default function Brand() {
  const { t } = useLanguage()
  return (
    <Link className="brand" to="/dashboard" aria-label={t("FlashMind home")}>
      <span className="brand-bubble"><img src={logo} alt="" /></span>
      <span className="brand-text">
        <span className="brand-title">FlashMind</span>
        <span className="brand-subtitle">{t("Master your learning")}</span>
      </span>
    </Link>
  )
}
