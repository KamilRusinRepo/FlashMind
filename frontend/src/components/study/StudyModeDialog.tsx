import { useLanguage } from '../../i18n/LanguageContext'
import { useEffect, useId, useRef, useState } from 'react'
import type { StudyMode } from '../../api/study'
import reviewIcon from '../../assets/icons/study/active_session.svg'
import practiceIcon from '../../assets/icons/study/daily_goal.svg'
import './StudyModeDialog.css'

export default function StudyModeDialog({ onClose, onStart, sending, error }: {
  onClose: () => void; onStart: (mode: StudyMode) => void; sending: boolean; error: string
}) {
  const { t } = useLanguage()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const groupName = useId()
  const [mode, setMode] = useState<StudyMode>('SCHEDULED_REVIEW')

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    dialog?.querySelector<HTMLInputElement>('input:checked')?.focus()
    return () => { if (dialog?.open) dialog.close() }
  }, [])

  return <dialog ref={dialogRef} className="study-mode-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    aria-busy={sending} onCancel={event => { event.preventDefault(); if (!sending) onClose() }}>
    <div className="study-mode-heading">
      <div><p className="study-mode-eyebrow">{t("YOUR NEXT SESSION")}</p><h2 id={titleId}>{t("How would you like to study?")}</h2></div>
      <button className="study-mode-close" type="button" disabled={sending} aria-label={t("Close study mode selection")} onClick={onClose}>×</button>
    </div>
    <p id={descriptionId} className="study-mode-description">{t("Follow your review schedule or practise at your own pace.")}</p>
    <fieldset className="study-mode-options" disabled={sending}>
      <legend className="study-mode-legend">{t("Choose a study mode")}</legend>
      <label className={`study-mode-option${mode === 'SCHEDULED_REVIEW' ? ' is-selected' : ''}`}>
        <input type="radio" name={groupName} value="SCHEDULED_REVIEW" checked={mode === 'SCHEDULED_REVIEW'} onChange={() => setMode('SCHEDULED_REVIEW')} />
        <span className="study-mode-icon"><img src={reviewIcon} alt="" /></span>
        <span className="study-mode-copy"><strong>{t("Scheduled Review")}</strong>
          <span>{t("Review up to 10 cards, starting with due reviews and then new cards.")}</span>
          <small>{t("Updates your review schedule")}</small></span>
      </label>
      <label className={`study-mode-option${mode === 'FREE_PRACTICE' ? ' is-selected' : ''}`}>
        <input type="radio" name={groupName} value="FREE_PRACTICE" checked={mode === 'FREE_PRACTICE'} onChange={() => setMode('FREE_PRACTICE')} />
        <span className="study-mode-icon"><img src={practiceIcon} alt="" /></span>
        <span className="study-mode-copy"><strong>{t("Free Practice")}</strong>
          <span>{t("Practise the whole deck whenever you like, regardless of review dates.")}</span>
          <small>{t("Keeps your review schedule unchanged")}</small></span>
      </label>
    </fieldset>
    {error && <p className="form-notice form-error study-mode-preview" role="alert">{t(error)}</p>}
    <div className="study-mode-actions">
      <button className="button button-outline" type="button" disabled={sending} onClick={onClose}>{t("Cancel")}</button>
      <button className="button button-primary" type="button" disabled={sending} onClick={() => onStart(mode)}>{sending ? t("Starting…") : t("Start session")}</button>
    </div>
  </dialog>
}
