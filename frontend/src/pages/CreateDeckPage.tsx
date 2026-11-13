import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { createDeck, CreateDeckError, type CreateDeckErrors, type CreateDeckRequest } from '../api/decks'
import { useAuth } from '../auth/AuthContext'
import FormField from '../components/FormField'
import SelectField from '../components/SelectField'
import './CreateDeck.css'

const languages = ['English', 'Polish', 'German', 'Spanish', 'French', 'Japanese']
const categories = ['Academic', 'Language', 'History', 'Science', 'Personal', 'Tech']

export default function CreateDeckPage() {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [deckType, setDeckType] = useState<CreateDeckRequest['deckType']>('GENERAL')
  const [errors, setErrors] = useState<CreateDeckErrors>({})
  const [error, setError] = useState('')
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
    setErrors(nextErrors)
    setError('')
    const first = Object.keys(nextErrors)[0]
    if (first) { form.querySelector<HTMLElement>(`[name="${first}"]`)?.focus(); return }

    submitting.current = true
    setSending(true)
    try {
      const created = await createDeck(request)
      if (mounted.current) navigate('/decks', { replace: true, state: { createdDeckId: created.id } })
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof CreateDeckError && cause.status === 401) { refresh(); return }
      setError(cause instanceof CreateDeckError ? cause.message : 'Unable to create your deck. Please try again.')
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

  return <div className="db-page">
    <Link to="/decks" className="back-link">← Back to My Decks</Link>
    <header className="db-section-heading decks-heading"><div><h1>Create New Deck</h1><p>Organize your learning with a custom set of flashcards.</p></div></header>
    <form className="deck-create-form" noValidate aria-busy={sending} onSubmit={handleSubmit} onInput={() => setError('')}>
      <div className="deck-form-grid">
        <div className="deck-form-wide"><FormField id="deck-name" name="name" label="Deck name" placeholder="e.g. English A1, Java Fundamentals…" maxLength={120} required disabled={sending} error={errors.name} /></div>
        <div className="form-field deck-form-wide"><label htmlFor="deck-description">Description (optional)</label>
          <div className={`input-wrapper${errors.description ? ' input-invalid' : ''}`}><textarea id="deck-description" name="description" rows={4} maxLength={2000}
            placeholder="What will you learn with this deck?" disabled={sending} aria-invalid={errors.description ? true : undefined} aria-describedby={errors.description ? 'deck-description-error' : undefined} /></div>
          {errors.description && <p className="field-error" id="deck-description-error">{errors.description}</p>}
        </div>
        <SelectField id="deck-type" name="deckType" label="Deck type" value={deckType} required disabled={sending} error={errors.deckType}
          onChange={event => { setDeckType(event.target.value === 'LANGUAGE' ? 'LANGUAGE' : 'GENERAL'); setErrors(current => ({ ...current, targetLanguage: undefined })) }}>
          <option value="GENERAL">General</option><option value="LANGUAGE">Language</option>
        </SelectField>
        <SelectField id="deck-category" name="category" label="Category (optional)" defaultValue="" disabled={sending} error={errors.category}>
          <option value="">No category</option>{categories.map(category => <option key={category}>{category}</option>)}
        </SelectField>
        <SelectField id="source-language" name="sourceLanguage" label="Source language" defaultValue="" required disabled={sending} error={errors.sourceLanguage}>
          <option value="">Select language</option>{languages.map(language => <option key={language}>{language}</option>)}
        </SelectField>
        {deckType === 'LANGUAGE' && <SelectField id="target-language" name="targetLanguage" label="Target language" defaultValue="" required disabled={sending} error={errors.targetLanguage}>
          <option value="">Select language</option>{languages.map(language => <option key={language}>{language}</option>)}
        </SelectField>}
        <div className="deck-form-wide"><FormField id="deck-background" name="backgroundUrl" label="Background image URL (optional)" type="url" placeholder="https://example.com/image.jpg"
          maxLength={2048} disabled={sending} error={errors.backgroundUrl} hint="Use an http or https link to an image." /></div>
      </div>
      <label className="deck-public-choice"><input type="checkbox" name="publicDeck" disabled={sending} />
        <span><strong>Make this deck public</strong><span>Allow other learners to discover it. Public decks stay available after account deletion.</span></span>
      </label>
      {error && <p className="form-notice form-error" role="alert">{error}</p>}
      {Object.keys(errors).some(field => errors[field as keyof CreateDeckErrors]) && <p className="field-error" role="alert">Please check the highlighted fields.</p>}
      <div className="deck-form-actions"><Link className="button button-outline" to="/decks">Cancel</Link><button className="button button-primary" type="submit" disabled={sending}>{sending ? 'Creating deck…' : 'Create Deck'}</button></div>
    </form>
  </div>
}
