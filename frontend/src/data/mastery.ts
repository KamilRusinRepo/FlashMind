import badIcon from '../assets/icons/statistics/bad.svg'
import averageIcon from '../assets/icons/statistics/average.svg'
import goodIcon from '../assets/icons/statistics/good.svg'
import veryGoodIcon from '../assets/icons/statistics/very_good.svg'

export type MasteryLevel = 1 | 2 | 3 | 4
export const masteryRatings = [
  { level: 1, label: 'Bad', tone: 'bad', icon: badIcon },
  { level: 2, label: 'Average', tone: 'average', icon: averageIcon },
  { level: 3, label: 'Good', tone: 'good', icon: goodIcon },
  { level: 4, label: 'Very good', tone: 'great', icon: veryGoodIcon },
] as const
