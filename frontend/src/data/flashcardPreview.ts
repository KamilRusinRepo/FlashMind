// UI examples only: these are not saved flashcards and never get sent to the API.
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

export const generalFlashcardExamples: PreviewFlashcard[] = [
  { id: 'example-recall', question: 'What is active recall?', answer: 'Retrieving information from memory instead of simply rereading it.' },
  { id: 'example-repetition', question: 'What is spaced repetition?', answer: 'Reviewing information at increasing intervals to help you remember it for longer.' },
  { id: 'example-focus', question: 'What makes a useful flashcard?', answer: 'One clear question with a concise answer that you can check.' },
]

export const languageFlashcardExamples: PreviewFlashcard[] = [
  { id: 'example-curiosity', question: 'curiosity', answer: 'ciekawość', exampleSentence: 'Curiosity helps us discover new things.', translatedExample: 'Ciekawość pomaga nam odkrywać nowe rzeczy.' },
  { id: 'example-habit', question: 'habit', answer: 'nawyk', exampleSentence: 'A little practice can become a daily habit.', translatedExample: 'Odrobina ćwiczeń może stać się codziennym nawykiem.' },
  { id: 'example-progress', question: 'progress', answer: 'postęp', exampleSentence: 'Small steps lead to steady progress.', translatedExample: 'Małe kroki prowadzą do stałego postępu.' },
]
