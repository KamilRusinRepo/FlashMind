import { useLanguage } from '../../i18n/LanguageContext'

export default function AdminPreviewAction({ icon, label, danger = false }: { icon: string; label: string; danger?: boolean }) {
  const { t } = useLanguage()
  return <button className={`admin-action-button${danger ? ' is-danger' : ''}`} type="button" disabled aria-label={label} title={t('{0} — coming soon', [label])}>
    <span className="admin-action-icon" aria-hidden="true" style={{ maskImage: `url("${icon}")` }} />
  </button>
}
