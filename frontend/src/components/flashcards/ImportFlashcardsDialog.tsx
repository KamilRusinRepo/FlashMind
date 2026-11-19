import { useId, useState, type ChangeEvent } from 'react'
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

export default function ImportFlashcardsDialog({ onCancel }: { onCancel: () => void }) {
  const { t, locale } = useLanguage()
  const id = useId()
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState('')

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null
    setError('')
    if (selected && !/\.csv$/i.test(selected.name)) {
      setFile(null)
      setError('Choose a file with the .csv extension.')
      event.target.value = ''
      return
    }
    setFile(selected)
  }

  return <FlashcardDialog title="Import CSV" onCancel={onCancel}>
    <div className="csv-import-content">
      <p>{t('Add multiple flashcards to this deck using a CSV file.')}</p>
      <p className="form-notice" role="note">{t('Import is not available yet. You can choose a file, but it will not be uploaded or saved.')}</p>
      <div className="csv-file-field">
        <label htmlFor={`${id}-file`}>{t('CSV file')}</label>
        <input id={`${id}-file`} type="file" accept=".csv,text/csv" onChange={selectFile}
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
        <button className="button button-outline" type="button" onClick={onCancel}>{t('Cancel')}</button>
        <button className="button button-primary" type="button" disabled aria-describedby={`${id}-preview-hint`}>{t('Preview import')}</button>
      </div>
      <p className="field-hint" id={`${id}-preview-hint`}>{t('File validation and the import preview will be available in a later step.')}</p>
    </div>
  </FlashcardDialog>
}
