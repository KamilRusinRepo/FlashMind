export type StudyCardData = {
  id: string; question: string; answer: string; exampleSentence?: string; translatedExample?: string; imageUrl?: string
}

export const languageStudyCards: StudyCardData[] = [
  { id: 'curiosity', question: 'curiosity', answer: 'ciekawość', exampleSentence: 'Curiosity helps us discover new things.', translatedExample: 'Ciekawość pomaga nam odkrywać nowe rzeczy.' },
  { id: 'habit', question: 'habit', answer: 'nawyk, przyzwyczajenie', exampleSentence: 'Practice can become a daily habit.', translatedExample: 'Ćwiczenia mogą stać się codziennym nawykiem.' },
  { id: 'progress', question: 'progress', answer: 'postęp', exampleSentence: 'Small steps lead to progress.', translatedExample: 'Małe kroki prowadzą do postępu.' },
]

export const generalStudyCards: StudyCardData[] = [
  { id: 'planet', question: 'Which planet is known as the Red Planet?', answer: 'Mars' },
  { id: 'ocean', question: 'What is the largest ocean on Earth?', answer: 'Pacific, Pacific Ocean' },
  { id: 'water', question: 'What is the chemical formula for water?', answer: 'H2O' },
]

export function studyAnswerOptions(answer: string): string[] {
  return answer.split(',').map(option => option.trim()).filter(Boolean)
}

export function matchesStudyAnswer(guess: string, answer: string): boolean {
  const normalized = guess.trim().toLowerCase()
  return normalized !== '' && studyAnswerOptions(answer).some(option => option.toLowerCase() === normalized)
}
