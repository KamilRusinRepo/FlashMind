import { useEffect, useId, useRef, type ReactNode } from 'react'

export default function FlashcardDialog({ title, onCancel, children, previewOnly = true }: { title: string; onCancel: () => void; children: ReactNode; previewOnly?: boolean }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    dialog?.querySelector<HTMLElement>('textarea, button')?.focus()
    return () => { if (dialog?.open) dialog.close() }
  }, [])

  return <dialog ref={dialogRef} className="flashcard-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!dialogRef.current?.querySelector('form[aria-busy="true"]')) onCancel() }}>
    <h2 id={titleId}>{title}</h2>
    {previewOnly && <p className="form-notice">Form preview only. Saving changes will be available soon.</p>}
    {children}
  </dialog>
}
