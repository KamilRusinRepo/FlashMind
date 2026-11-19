import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { CsvImportApiError, importFlashcardsCsv, previewFlashcardsCsv, type CsvPreview } from '../../api/flashcardCsv'
import { FlashcardApiError } from '../../api/flashcards'
import { useAuth } from '../../auth/AuthContext'
import CsvImportPreview from './CsvImportPreview'
import { useLanguage } from '../../i18n/LanguageContext'
import exampleCsv from '../../assets/examples/flashcards-example.csv?url'
import FlashcardDialog from './FlashcardDialog'
import './ImportFlashcardsDialog.css'

const columns = [
  { name: 'question', description: 'Question or front of the flashcard', required: true },
  { name: 'answer', description: 'Answer or back of the flashcard', required: true },
  { name: 'example_sentence', description: 'Example sentence', required: false },
  { name: 'translated_example', description: 'Translation of the example sentence', required: false },
  { name: 'image_url', description: 'Direct HTTP or HTTPS link to an image', required: false },
]

export default function ImportFlashcardsDialog({ deckId, onCancel, onImported, onRefresh }: {
  deckId: number; onCancel: () => void; onImported: (count: number) => void; onRefresh: () => void
}) {
  const { t, locale } = useLanguage()
  const id = useId()
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [preview, setPreview] = useState<CsvPreview | null>(null)
  const [sending, setSending] = useState(false)
  const [saving, setSaving] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const active = useRef<AbortController | null>(null)
  const confirmationRef = useRef<HTMLElement>(null)
  const { refresh } = useAuth()
  useEffect(() => () => active.current?.abort(), [])
  useEffect(() => { if (confirming) confirmationRef.current?.focus() }, [confirming])

  async function analyse(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || active.current || uncertain || confirming) return
    const controller = new AbortController()
    active.current = controller; setSending(true); setError(''); setPreview(null)
    try {
      const result = await previewFlashcardsCsv(deckId, file, controller.signal)
      if (!controller.signal.aborted) setPreview(result)
    } catch (cause) {
      if (controller.signal.aborted) return
      if (cause instanceof FlashcardApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      setError(cause instanceof FlashcardApiError ? cause.message : 'Unable to analyse the CSV file. Please try again.')
    } finally {
      if (!controller.signal.aborted) setSending(false)
      if (active.current === controller) active.current = null
    }
  }

  async function importCards() {
    if (!file || !preview?.valid || !confirming || active.current || uncertain) return
    const controller = new AbortController()
    active.current = controller; setSending(true); setSaving(true); setError('')
    try {
      const count = await importFlashcardsCsv(deckId, file, preview.totalRows, controller.signal)
      if (!controller.signal.aborted) onImported(count)
    } catch (cause) {
      if (controller.signal.aborted) return
      if (cause instanceof FlashcardApiError && (cause.status === 401 || cause.status === 403)) { refresh(); return }
      const unknownResult = !(cause instanceof CsvImportApiError) || cause.uncertain
      setUncertain(unknownResult); setConfirming(false)
      if (!unknownResult) setPreview(null)
      setError(cause instanceof CsvImportApiError ? cause.message : 'Unable to confirm the import. Refresh flashcards before trying again to avoid adding duplicates.')
    } finally {
      if (!controller.signal.aborted) { setSending(false); setSaving(false) }
      if (active.current === controller) active.current = null
    }
  }

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null
    setError(''); setPreview(null); setConfirming(false)
    if (selected && !/\.csv$/i.test(selected.name)) {
      setFile(null)
      setError('Choose a file with the .csv extension.')
      event.target.value = ''
      return
    }
    if (selected && selected.size > 1024 * 1024) {
      setFile(null); setError('CSV file must not exceed 1 MiB.'); event.target.value = ''; return
    }
    setFile(selected)
  }

  return <FlashcardDialog title="Import CSV" onCancel={() => { if (!active.current) onCancel() }}>
    <form className="csv-import-content" noValidate aria-busy={sending} onSubmit={event => void analyse(event)}>
      <p>{t('Add multiple flashcards to this deck using a CSV file.')}</p>
      <p className="form-notice" role="note">{t('Preview the file first. Flashcards are saved only after you confirm the import.')}</p>
      <p className="field-hint">{t('Maximum file size: 1 MiB. Maximum flashcards: 1000.')}</p>
      <div className="csv-file-field">
        <label htmlFor={`${id}-file`}>{t('CSV file')}</label>
        <input id={`${id}-file`} type="file" accept=".csv,text/csv" onChange={selectFile} disabled={sending || confirming || uncertain}
          aria-invalid={!!error} aria-describedby={`${id}-format${error ? ` ${id}-error` : ''}`} />
        <p className="field-hint" id={`${id}-format`}>{t('Use UTF-8 encoding, commas as separators and a header row with the column names below.')}</p>
        {error && <p className="field-error" id={`${id}-error`} role="alert">{t(error)}</p>}
        {file && <p className="csv-selected-file" role="status">{t('Selected file: {0} ({1} KB)', [file.name,
          new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(file.size / 1024)])}</p>}
      </div>
      <section aria-labelledby={`${id}-columns`}>
        <h3 id={`${id}-columns`}>{t('CSV columns')}</h3>
        <dl className="csv-columns">{columns.map(column => <div key={column.name}>
          <dt><code>{column.name}</code><span className={column.required ? 'csv-required' : ''}>{t(column.required ? 'Required' : 'Optional')}</span></dt>
          <dd>{t(column.description)}</dd>
        </div>)}</dl>
        <p className="field-hint">{t('The question and answer must not be empty. Other columns may be omitted or left blank.')}</p>
        <p className="field-hint">{t('Put values containing commas or line breaks in double quotes. Write a double quote inside a value as two double quotes.')}</p>
      </section>
      <a className="button button-outline csv-example-download" href={exampleCsv} download="flashcards-example.csv">{t('Download example CSV')}</a>
      <div className="flashcard-dialog-actions">
        <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!active.current) onCancel() }}>{t('Cancel')}</button>
        <button className="button button-primary" type="submit" disabled={!file || sending || confirming || uncertain}>{sending && !saving ? t('Analysing CSV…') : t('Preview import')}</button>
      </div>
      {preview && <CsvImportPreview key={file?.name} preview={preview} disabled={sending || confirming || uncertain} onImport={() => { if (!active.current) { setError(''); setConfirming(true) } }} />}
      {confirming && preview && <section ref={confirmationRef} tabIndex={-1} className="csv-import-confirmation" aria-label={t('Confirm CSV import')}>
        <h3>{t('Confirm CSV import')}</h3>
        <p>{t('Add {0} flashcards to this deck? Existing flashcards will remain. Duplicate rows will be added as new flashcards.', [preview.totalRows.toLocaleString(locale)])}</p>
        <div className="flashcard-dialog-actions">
          <button className="button button-outline" type="button" disabled={sending} onClick={() => { if (!active.current) setConfirming(false) }}>{t('Back to preview')}</button>
          <button className="button button-primary" type="button" disabled={sending} onClick={() => void importCards()}>{saving ? t('Importing flashcards…') : t('Confirm import')}</button>
        </div>
      </section>}
      {uncertain && <button className="button button-outline" type="button" onClick={onRefresh}>{t('Refresh flashcards')}</button>}
    </form>
  </FlashcardDialog>
}
