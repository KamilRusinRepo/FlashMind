import { useState } from 'react'
import type { CsvCardData, CsvPreview } from '../../api/flashcardCsv'
import { useLanguage } from '../../i18n/LanguageContext'

const fields: { name: keyof CsvCardData; label: string }[] = [
  { name: 'question', label: 'question' }, { name: 'answer', label: 'answer' },
  { name: 'exampleSentence', label: 'example_sentence' }, { name: 'translatedExample', label: 'translated_example' }, { name: 'imageUrl', label: 'image_url' },
]

export default function CsvImportPreview({ preview }: { preview: CsvPreview }) {
  const { t, locale } = useLanguage()
  const [page, setPage] = useState(0)
  const pages = Math.ceil(preview.rows.length / 20)
  return <section className="csv-preview" aria-label={t('CSV import preview')}>
    <h3>{t('CSV import preview')}</h3>
    <p role="status">{t('Analysed: {0}. Valid: {1}. Invalid: {2}.', [preview.totalRows.toLocaleString(locale), preview.validRows.toLocaleString(locale), preview.invalidRows.toLocaleString(locale)])}</p>
    <p className={`form-notice ${preview.valid ? 'form-success' : 'form-error'}`} role={preview.valid ? 'status' : 'alert'}>
      {t(preview.valid ? 'The file is valid. No flashcards have been saved.' : 'Fix the reported errors and analyse the file again. No flashcards have been saved.')}
    </p>
    {preview.errors.length > 0 && <ul className="csv-file-errors">{preview.errors.map((error, index) => <li key={index}>
      <strong>{t('Line {0}', [error.rowNumber.toLocaleString(locale)])}: </strong>{t(error.message)}
    </li>)}</ul>}
    {preview.errors.length > 0 && <p className="field-hint">{t('Analysis stopped because of file errors. The preview may be incomplete.')}</p>}
    <div className="csv-preview-rows">{preview.rows.slice(page * 20, (page + 1) * 20).map(row => <article key={row.rowNumber} className={`csv-preview-row${Object.keys(row.fieldErrors).length ? ' is-invalid' : ''}`}>
      <h4>{t('Line {0}', [row.rowNumber.toLocaleString(locale)])}</h4>
      {row.data && <dl>{fields.map(field => <div key={field.name}><dt><code>{field.label}</code></dt><dd>{row.data?.[field.name] || '—'}</dd></div>)}</dl>}
      {Object.keys(row.fieldErrors).length > 0 && <ul className="field-error">{Object.entries(row.fieldErrors).map(([field, message]) => <li key={field}>
        {field !== 'row' && <><code>{field}</code>: </>}{t(message)}
      </li>)}</ul>}
    </article>)}</div>
    {pages > 1 && <nav className="csv-preview-pagination" aria-label={t('CSV preview pagination')}>
      <button className="button button-outline" type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>{t('← Previous')}</button>
      <span>{t('Page {0} of {1}', [(page + 1).toLocaleString(locale), pages.toLocaleString(locale)])}</span>
      <button className="button button-outline" type="button" disabled={page + 1 >= pages} onClick={() => setPage(value => value + 1)}>{t('Next →')}</button>
    </nav>}
    <p className="field-hint">{t('Line numbers refer to the first physical line of each CSV record. Blank lines are ignored.')}</p>
    <button className="button button-primary" type="button" disabled>{t('Import flashcards')}</button>
    <p className="field-hint">{t('Saving imported flashcards will be available in the next step.')}</p>
  </section>
}
