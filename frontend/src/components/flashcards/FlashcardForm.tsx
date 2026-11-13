import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { FlashcardApiError } from '../../api/flashcards'
import { useAuth } from '../../auth/AuthContext'
import { isFlashcardImageUrl, type PreviewFlashcard } from '../../data/flashcardPreview'
import FormField from '../FormField'
import FlashcardImage from './FlashcardImage'

export type FlashcardValues = Omit<PreviewFlashcard, 'id'>
type Field = keyof FlashcardValues
const fields: { name: Field; label: string; required?: boolean; limit: number }[] = [
  { name: 'question', label: 'Front · Question', required: true, limit: 2000 },
  { name: 'answer', label: 'Back · Answer', required: true, limit: 2000 },
  { name: 'exampleSentence', label: 'Example sentence (optional)', limit: 1000 },
  { name: 'translatedExample', label: 'Example translation (optional)', limit: 1000 },
  { name: 'imageUrl', label: 'Image URL (optional)', limit: 2048 },
]

export default function FlashcardForm({ initialValues, onSubmit, onCancel, previewOnly = false }: {
  initialValues?: FlashcardValues; onSubmit: (values: FlashcardValues) => void | Promise<void>; onCancel: () => void; previewOnly?: boolean
}) {
  const id = useId()
  const formRef = useRef<HTMLFormElement>(null)
  const [values, setValues] = useState<FlashcardValues>(initialValues ?? { question: '', answer: '', exampleSentence: '', translatedExample: '', imageUrl: '' })
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const { refresh } = useAuth()
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (previewOnly || submitting.current) return
    setError('')
    const nextErrors: Partial<Record<Field, string>> = {}
    for (const field of fields) {
      const value = (values[field.name] ?? '').trim()
      if (field.required && !value) nextErrors[field.name] = 'This field is required.'
      else if (value.length > field.limit) nextErrors[field.name] = `Use ${field.limit} characters or fewer.`
      else if (field.name === 'imageUrl' && value && !isFlashcardImageUrl(value)) nextErrors.imageUrl = 'Enter a full http:// or https:// image URL without embedded credentials.'
    }
    setErrors(nextErrors)
    const firstError = fields.find(field => nextErrors[field.name])
    if (firstError) { formRef.current?.querySelector<HTMLElement>(`[name="${firstError.name}"]`)?.focus(); return }
    submitting.current = true
    setSending(true)
    try {
      await onSubmit({ question: values.question.trim(), answer: values.answer.trim(),
        exampleSentence: values.exampleSentence?.trim() || undefined, translatedExample: values.translatedExample?.trim() || undefined,
        imageUrl: values.imageUrl?.trim() || undefined })
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof FlashcardApiError && cause.status === 401) { refresh(); return }
      setError(cause instanceof FlashcardApiError ? cause.message : 'Unable to save the flashcard. Please try again.')
      if (cause instanceof FlashcardApiError) {
        setErrors(cause.fieldErrors)
        const field = fields.find(item => cause.fieldErrors[item.name])
        if (field) requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>(`[name="${field.name}"]`)?.focus())
      }
    } finally {
      submitting.current = false
      if (mounted.current) setSending(false)
    }
  }

  return <form ref={formRef} className="flashcard-form" noValidate aria-busy={sending} onSubmit={handleSubmit}>
    {error && <p className="form-notice form-error" role="alert">{error}</p>}
    <div className="flashcard-form-fields">{fields.map(field => {
      const fieldId = `${id}-${field.name}`
      if (field.name === 'imageUrl') return <div className="flashcard-image-field" key={field.name}>
        <FormField id={fieldId} name="imageUrl" type="url" label={field.label} maxLength={field.limit}
          placeholder="https://example.com/image.jpg" hint="Paste a direct link to an image (HTTP or HTTPS). Clear this field to remove it."
          value={values.imageUrl ?? ''} error={errors.imageUrl} disabled={sending}
          onChange={event => { setValues(current => ({ ...current, imageUrl: event.target.value })); setErrors(current => ({ ...current, imageUrl: undefined })) }} />
        <FlashcardImage url={values.imageUrl?.trim()} />
      </div>
      return <div className="form-field" key={field.name}>
        <label htmlFor={fieldId}>{field.label}{field.required && ' *'}</label>
        <textarea id={fieldId} name={field.name} rows={field.required ? 4 : 3} required={field.required} maxLength={field.limit} disabled={sending}
          value={values[field.name] ?? ''} aria-invalid={errors[field.name] ? true : undefined}
          aria-describedby={`${fieldId}-hint${errors[field.name] ? ` ${fieldId}-error` : ''}`}
          onChange={event => { setValues(current => ({ ...current, [field.name]: event.target.value })); setErrors(current => ({ ...current, [field.name]: undefined })) }} />
        <p id={`${fieldId}-hint`} className="field-hint">{(values[field.name] ?? '').length} / {field.limit}</p>
        {errors[field.name] && <p id={`${fieldId}-error`} className="field-error" role="alert">{errors[field.name]}</p>}
      </div>
    })}</div>
    <div className="flashcard-dialog-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!submitting.current) onCancel() }}>Cancel</button>
      <button className="button button-primary" type="submit" disabled={previewOnly || sending}>{sending ? 'Saving…' : previewOnly ? 'Saving coming soon' : initialValues ? 'Save changes' : 'Add Flashcard'}</button>
    </div>
  </form>
}
