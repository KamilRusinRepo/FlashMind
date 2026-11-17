import { useLanguage } from '../i18n/LanguageContext'
import type { ReactNode, SelectHTMLAttributes } from 'react'

type Props = SelectHTMLAttributes<HTMLSelectElement> & { id: string; label: string; error?: string; children: ReactNode }

export default function SelectField({ id, label, error, children, ...props }: Props) {
  const { t } = useLanguage()
  return <div className="form-field">
    <label htmlFor={id}>{t(label)}</label>
    <div className={`input-wrapper${error ? ' input-invalid' : ''}`}>
      <select {...props} id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}>{children}</select>
    </div>
    {error && <p className="field-error" id={`${id}-error`}>{t(error)}</p>}
  </div>
}
