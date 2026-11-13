// Shared display/form shape. API data is mapped to this shape without exposing entities.
export type PreviewFlashcard = {
  id: string
  question: string
  answer: string
  exampleSentence?: string
  translatedExample?: string
  imageUrl?: string
}

export function isFlashcardImageUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return (url.protocol === 'https:' || url.protocol === 'http:') && !!url.hostname && !url.username && !url.password
  } catch {
    return false
  }
}
