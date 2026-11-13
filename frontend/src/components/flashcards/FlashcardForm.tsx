import { useId, useRef, useState, type FormEvent } from 'react'
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
  initialValues?: FlashcardValues; onSubmit: (values: FlashcardValues) => void; onCancel: () => void; previewOnly?: boolean
}) {
  const id = useId()
  const formRef = useRef<HTMLFormElement>(null)
  const [values, setValues] = useState<FlashcardValues>(initialValues ?? { question: '', answer: '', exampleSentence: '', translatedExample: '', imageUrl: '' })
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (previewOnly) return
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
    onSubmit({ question: values.question.trim(), answer: values.answer.trim(),
      exampleSentence: values.exampleSentence?.trim() || undefined, translatedExample: values.translatedExample?.trim() || undefined,
      imageUrl: values.imageUrl?.trim() || undefined })
  }

  return <form ref={formRef} className="flashcard-form" noValidate onSubmit={handleSubmit}>
    <div className="flashcard-form-fields">{fields.map(field => {
      const fieldId = `${id}-${field.name}`
      if (field.name === 'imageUrl') return <div className="flashcard-image-field" key={field.name}>
        <FormField id={fieldId} name="imageUrl" type="url" label={field.label} maxLength={field.limit}
          placeholder="https://example.com/image.jpg" hint="Paste a direct link to an image (HTTP or HTTPS). Clear this field to remove it."
          value={values.imageUrl ?? ''} error={errors.imageUrl}
          onChange={event => { setValues(current => ({ ...current, imageUrl: event.target.value })); setErrors(current => ({ ...current, imageUrl: undefined })) }} />
        <FlashcardImage url={values.imageUrl?.trim()} />
      </div>
      return <div className="form-field" key={field.name}>
        <label htmlFor={fieldId}>{field.label}{field.required && ' *'}</label>
        <textarea id={fieldId} name={field.name} rows={field.required ? 4 : 3} required={field.required} maxLength={field.limit}
          value={values[field.name] ?? ''} aria-invalid={errors[field.name] ? true : undefined}
          aria-describedby={`${fieldId}-hint${errors[field.name] ? ` ${fieldId}-error` : ''}`}
          onChange={event => { setValues(current => ({ ...current, [field.name]: event.target.value })); setErrors(current => ({ ...current, [field.name]: undefined })) }} />
        <p id={`${fieldId}-hint`} className="field-hint">{(values[field.name] ?? '').length} / {field.limit}</p>
        {errors[field.name] && <p id={`${fieldId}-error`} className="field-error" role="alert">{errors[field.name]}</p>}
      </div>
    })}</div>
    <div className="flashcard-dialog-actions">
      <button className="button button-outline" type="button" onClick={onCancel}>Cancel</button>
      <button className="button button-primary" type="submit" disabled={previewOnly}>{previewOnly ? 'Saving coming soon' : initialValues ? 'Update preview' : 'Add to preview'}</button>
    </div>
  </form>
}
