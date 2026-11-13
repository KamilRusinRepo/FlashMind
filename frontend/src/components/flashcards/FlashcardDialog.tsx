import { useEffect, useId, useRef, type ReactNode } from 'react'

export default function FlashcardDialog({ title, onCancel, children }: { title: string; onCancel: () => void; children: ReactNode }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    dialog?.querySelector<HTMLElement>('textarea, button')?.focus()
    return () => { if (dialog?.open) dialog.close() }
  }, [])

  return <dialog ref={dialogRef} className="flashcard-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); onCancel() }}>
    <h2 id={titleId}>{title}</h2>
    <p className="form-notice">Form preview only. Saving changes will be available soon.</p>
    {children}
  </dialog>
}
