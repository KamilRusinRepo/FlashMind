import { useLanguage } from '../../i18n/LanguageContext'

export default function StatisticsStatus({ message, retry }: { message?: string; retry?: () => void }) {
  const { t } = useLanguage()
  return <div className="statistics-panel">
    {message ? <><p className="form-notice form-error" role="alert">{t(message)}</p><button className="button button-outline" type="button" onClick={retry}>{t('Try again')}</button></>
      : <p role="status">{t('Loading statistics…')}</p>}
  </div>
}
