import type { ReactNode, SelectHTMLAttributes } from 'react'

type Props = SelectHTMLAttributes<HTMLSelectElement> & { id: string; label: string; error?: string; children: ReactNode }

export default function SelectField({ id, label, error, children, ...props }: Props) {
  return <div className="form-field">
    <label htmlFor={id}>{label}</label>
    <div className={`input-wrapper${error ? ' input-invalid' : ''}`}>
      <select {...props} id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}>{children}</select>
    </div>
    {error && <p className="field-error" id={`${id}-error`}>{error}</p>}
  </div>
}
