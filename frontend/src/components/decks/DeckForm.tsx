import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { CreateDeckError, type OwnedDeck, type CreateDeckErrors, type CreateDeckRequest } from '../../api/decks'
import { useAuth } from '../../auth/AuthContext'
import FormField from '../FormField'
import SelectField from '../SelectField'
import '../../pages/CreateDeck.css'

const languages = ['English', 'Polish', 'German', 'Spanish', 'French', 'Japanese']
const categories = ['Academic', 'Language', 'History', 'Science', 'Personal', 'Tech']

function withCurrentValue(options: string[], current?: string | null) {
  return current && !options.includes(current) ? [...options, current] : options
}

type Props = {
  initialValues?: OwnedDeck
  save?: (request: CreateDeckRequest) => Promise<OwnedDeck>
  onSaved?: (deck: OwnedDeck) => void
  submitLabel?: string
  pendingLabel?: string
}

export default function DeckForm({ initialValues, save, onSaved, submitLabel = 'Create Deck', pendingLabel = 'Creating deck…' }: Props) {
  const { refresh } = useAuth()
  const [deckType, setDeckType] = useState<CreateDeckRequest['deckType']>(initialValues?.deckType ?? 'GENERAL')
  const [errors, setErrors] = useState<CreateDeckErrors>({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    const form = event.currentTarget
    const data = new FormData(form)
    const value = (name: string) => String(data.get(name) ?? '').trim()
    const request: CreateDeckRequest = {
      name: value('name'), description: value('description') || null, deckType,
      sourceLanguage: value('sourceLanguage'), targetLanguage: deckType === 'LANGUAGE' ? value('targetLanguage') || null : null,
      category: value('category') || null, backgroundUrl: value('backgroundUrl') || null, publicDeck: data.get('publicDeck') === 'on',
    }
    const nextErrors: CreateDeckErrors = {}
    if (!request.name) nextErrors.name = 'Deck name is required.'
    else if (request.name.length > 120) nextErrors.name = 'Use at most 120 characters.'
    if ((request.description?.length ?? 0) > 2000) nextErrors.description = 'Use at most 2000 characters.'
    if (!request.sourceLanguage) nextErrors.sourceLanguage = 'Select a source language.'
    if (deckType === 'LANGUAGE' && !request.targetLanguage) nextErrors.targetLanguage = 'Select a target language.'
    if (request.backgroundUrl) {
      try {
        const url = new URL(request.backgroundUrl)
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || request.backgroundUrl.length > 2048) throw new Error()
      } catch { nextErrors.backgroundUrl = 'Enter a valid http or https URL without embedded credentials.' }
    }
    setNotice('')
    setErrors(nextErrors)
    setError('')
    const first = Object.keys(nextErrors)[0]
    if (first) { form.querySelector<HTMLElement>(`[name="${first}"]`)?.focus(); return }

    if (!save) {
      setNotice('Editing is coming soon. No changes have been saved.')
      return
    }
    submitting.current = true
    setSending(true)
    try {
      const created = await save(request)
      if (mounted.current) onSaved?.(created)
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof CreateDeckError && cause.status === 401) { refresh(); return }
      setError(cause instanceof CreateDeckError ? cause.message : 'Unable to save your deck. Please try again.')
      if (cause instanceof CreateDeckError) {
        setErrors(cause.fieldErrors)
        const field = Object.keys(cause.fieldErrors)[0]
        if (field) requestAnimationFrame(() => form.querySelector<HTMLElement>(`[name="${field}"]`)?.focus())
      }
    } finally {
      submitting.current = false
      if (mounted.current) setSending(false)
    }
  }

  return (
    <form className="deck-create-form" noValidate aria-busy={sending} onSubmit={handleSubmit} onInput={() => { setError(''); setNotice('') }}>
      <div className="deck-form-grid">
        <div className="deck-form-wide"><FormField id="deck-name" name="name" label="Deck name" placeholder="e.g. English A1, Java Fundamentals…" defaultValue={initialValues?.name ?? ''} maxLength={120} required disabled={sending} error={errors.name} /></div>
        <div className="form-field deck-form-wide"><label htmlFor="deck-description">Description (optional)</label>
          <div className={`input-wrapper${errors.description ? ' input-invalid' : ''}`}><textarea id="deck-description" name="description" defaultValue={initialValues?.description ?? ''} rows={4} maxLength={2000}
            placeholder="What will you learn with this deck?" disabled={sending} aria-invalid={errors.description ? true : undefined} aria-describedby={errors.description ? 'deck-description-error' : undefined} /></div>
          {errors.description && <p className="field-error" id="deck-description-error">{errors.description}</p>}
        </div>
        <SelectField id="deck-type" name="deckType" label="Deck type" value={deckType} required disabled={sending} error={errors.deckType}
          onChange={event => { setDeckType(event.target.value === 'LANGUAGE' ? 'LANGUAGE' : 'GENERAL'); setErrors(current => ({ ...current, targetLanguage: undefined })) }}>
          <option value="GENERAL">General</option><option value="LANGUAGE">Language</option>
        </SelectField>
        <SelectField id="deck-category" name="category" label="Category (optional)" defaultValue={initialValues?.category ?? ''} disabled={sending} error={errors.category}>
          <option value="">No category</option>{withCurrentValue(categories, initialValues?.category).map(category => <option key={category}>{category}</option>)}
        </SelectField>
        <SelectField id="source-language" name="sourceLanguage" label="Source language" defaultValue={initialValues?.sourceLanguage ?? ''} required disabled={sending} error={errors.sourceLanguage}>
          <option value="">Select language</option>{withCurrentValue(languages, initialValues?.sourceLanguage).map(language => <option key={language}>{language}</option>)}
        </SelectField>
        {deckType === 'LANGUAGE' && <SelectField id="target-language" name="targetLanguage" label="Target language" defaultValue={initialValues?.targetLanguage ?? ''} required disabled={sending} error={errors.targetLanguage}>
          <option value="">Select language</option>{withCurrentValue(languages, initialValues?.targetLanguage).map(language => <option key={language}>{language}</option>)}
        </SelectField>}
        <div className="deck-form-wide"><FormField id="deck-background" name="backgroundUrl" label="Background image URL (optional)" type="url" placeholder="https://example.com/image.jpg"
          defaultValue={initialValues?.backgroundUrl ?? ''} maxLength={2048} disabled={sending} error={errors.backgroundUrl} hint="Use an http or https link to an image." /></div>
      </div>
      <label className="deck-public-choice"><input type="checkbox" name="publicDeck" defaultChecked={initialValues?.publicDeck ?? false} disabled={sending} />
        <span><strong>Make this deck public</strong><span>Allow other learners to discover it. Public decks stay available after account deletion.</span></span>
      </label>
      {notice && <p className="form-notice" role="status">{notice}</p>}
      {error && <p className="form-notice form-error" role="alert">{error}</p>}
      {Object.keys(errors).some(field => errors[field as keyof CreateDeckErrors]) && <p className="field-error" role="alert">Please check the highlighted fields.</p>}
      <div className="deck-form-actions"><Link className="button button-outline" to="/decks">Cancel</Link><button className="button button-primary" type="submit" disabled={sending}>{sending ? pendingLabel : submitLabel}</button></div>
    </form>
  )
}
