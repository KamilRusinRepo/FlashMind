import { useState, type InputHTMLAttributes } from 'react'
import eye from '../assets/icons/eye.svg'
import eyeOff from '../assets/icons/eye-off.svg'

type FormFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  id: string
  label: string
  error?: string
  hint?: string
}

export default function FormField({ id, label, error, hint, type = 'text', ...inputProps }: FormFieldProps) {
  const [visible, setVisible] = useState(false)
  const isPassword = type === 'password'
  const descriptionIds = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ')

  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <div className={`input-wrapper${error ? ' input-invalid' : ''}`}>
        <input
          {...inputProps}
          id={id}
          type={isPassword && visible ? 'text' : type}
          aria-invalid={error ? true : undefined}
          aria-describedby={descriptionIds || undefined}
        />
        {isPassword && (
          <button
            className="password-toggle"
            type="button"
            disabled={inputProps.disabled}
            aria-label={`${visible ? 'Hide' : 'Show'} ${label.toLowerCase()}`}
            aria-pressed={visible}
            aria-controls={id}
            onClick={() => setVisible((value) => !value)}
          >
            <img src={visible ? eyeOff : eye} alt="" />
          </button>
        )}
      </div>
      {hint && <p className="field-hint" id={`${id}-hint`}>{hint}</p>}
      {error && <p className="field-error" id={`${id}-error`}>{error}</p>}
    </div>
  )
}
