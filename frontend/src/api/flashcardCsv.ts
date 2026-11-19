import { FlashcardApiError } from './flashcards'
import { postWithCsrf } from './csrf'

export type CsvCardData = { question: string | null; answer: string | null; exampleSentence: string | null; translatedExample: string | null; imageUrl: string | null }
export type CsvPreviewRow = { rowNumber: number; data: CsvCardData | null; fieldErrors: Record<string, string> }
export type CsvPreview = { rows: CsvPreviewRow[]; errors: { rowNumber: number; message: string }[]; totalRows: number; validRows: number; invalidRows: number; valid: boolean }

export async function previewFlashcardsCsv(deckId: number, file: File, signal: AbortSignal): Promise<CsvPreview> {
  const form = new FormData()
  form.append('file', file)
  let response: Response
  try {
    response = await postWithCsrf(`/api/decks/${deckId}/flashcards/import/preview`, {
      body: form, headers: { Accept: 'application/json' }, signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
    })
  } catch { throw new FlashcardApiError('Unable to analyse the CSV file. Check your connection and try again.') }
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new FlashcardApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This deck is unavailable or does not belong to your account.'
      : response.status === 413 ? 'CSV file must not exceed 1 MiB.'
        : response.status === 403 ? 'You do not have permission to access this resource.'
          : response.status === 400 && data?.fieldErrors?.file === 'Choose a file with the .csv extension.' ? data.fieldErrors.file
            : 'Unable to analyse the CSV file. Please try again.', response.status)
  if (response.status !== 200 || !isCsvPreview(data)) throw new FlashcardApiError('The server returned an unexpected CSV preview. Please try again.')
  return data
}

function isCsvPreview(value: unknown): value is CsvPreview {
  if (!value || typeof value !== 'object') return false
  const data = value as CsvPreview
  const positive = (number: unknown) => Number.isSafeInteger(number) && Number(number) > 0
  if (!Array.isArray(data.rows) || data.rows.length > 1000 || data.totalRows !== data.rows.length
    || !Number.isSafeInteger(data.validRows) || data.validRows < 0 || !Number.isSafeInteger(data.invalidRows) || data.invalidRows < 0
    || data.validRows + data.invalidRows !== data.totalRows || typeof data.valid !== 'boolean'
    || !Array.isArray(data.errors) || !data.errors.every(error => error && positive(error.rowNumber) && typeof error.message === 'string')) return false
  let good = 0
  let previous = 1
  for (const row of data.rows) {
    if (!row || !positive(row.rowNumber) || row.rowNumber <= previous || !row.fieldErrors || typeof row.fieldErrors !== 'object'
      || Array.isArray(row.fieldErrors) || !Object.entries(row.fieldErrors).every(([field, message]) =>
        ['row', 'question', 'answer', 'example_sentence', 'translated_example', 'image_url'].includes(field) && typeof message === 'string')) return false
    previous = row.rowNumber
    if (row.data !== null && (!row.data || typeof row.data !== 'object'
      || !['question', 'answer', 'exampleSentence', 'translatedExample', 'imageUrl'].every(field => {
        const entry = row.data?.[field as keyof CsvCardData]
        return entry === null || typeof entry === 'string'
      }))) return false
    if (!Object.keys(row.fieldErrors).length) { if (!row.data) return false; good++ }
  }
  return good === data.validRows && data.valid === (data.errors.length === 0 && data.totalRows > 0 && good === data.totalRows)
}

export async function exportFlashcardsCsv(deckId: number, signal: AbortSignal): Promise<Blob> {
  let response: Response
  try {
    response = await fetch(`/api/decks/${deckId}/flashcards/export`, {
      credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'text/csv, application/json' },
      signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
    })
  } catch { throw new FlashcardApiError('Unable to export flashcards. Check your connection and try again.') }
  if (!response.ok) throw new FlashcardApiError(response.status === 401 ? 'Your session has expired. Please sign in again.'
    : response.status === 404 ? 'This deck is unavailable or does not belong to your account.'
      : 'Unable to export flashcards. Please try again.', response.status)
  if (response.status !== 200 || response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'text/csv') {
    throw new FlashcardApiError('The server returned an unexpected CSV file. Please try again.')
  }
  try { return await response.blob() }
  catch { throw new FlashcardApiError('Unable to export flashcards. Check your connection and try again.') }
}
