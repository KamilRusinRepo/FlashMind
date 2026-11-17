import { useRef, useState } from 'react'
import { ExploreApiError, setDeckFollowing } from '../../api/explore'
import { useAuth } from '../../auth/AuthContext'
import { useLanguage } from '../../i18n/LanguageContext'
import followIcon from '../../assets/icons/explore/follow.svg'

export type FollowState = { following: boolean; followerCount: number }

export default function FollowDeckButton({ deckId, following, ownDeck = false, followerCount = 0, onChanged }: {
  deckId: number; following: boolean; ownDeck?: boolean; followerCount?: number; onChanged: (state: FollowState) => void
}) {
  const { t } = useLanguage()
  const { refresh } = useAuth()
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const busy = useRef(false)
  async function toggle() {
    if (busy.current || ownDeck) return
    busy.current = true; setSending(true); setError('')
    try {
      const result = await setDeckFollowing(deckId, !following)
      onChanged(result ?? { following: false, followerCount: Math.max(0, followerCount - 1) })
    } catch (cause) {
      if (cause instanceof ExploreApiError && cause.status === 401) refresh()
      setError(cause instanceof ExploreApiError ? cause.message : 'Unable to change deck following. Please try again.')
    } finally { busy.current = false; setSending(false) }
  }
  return <div className="follow-deck-control">
    <button className={`button button-outline explore-follow-button${ownDeck ? ' is-owned' : ''}`} type="button" disabled={ownDeck || sending} aria-pressed={following} onClick={toggle}>
      {!ownDeck && !following && <span className="explore-follow-icon" aria-hidden="true" style={{ maskImage: `url("${followIcon}")` }} />}
      {sending ? t('Saving…') : ownDeck ? t('Your Deck') : following ? t('Unfollow') : t('Follow Deck')}
    </button>
    {error && <p className="form-notice form-error" role="alert">{t(error)}</p>}
  </div>
}
