import type { ChangeEvent, Ref } from 'react'

export const INVENTORY_PHOTO_ACCEPT = 'image/jpeg,image/png,image/webp'

export function InventoryPhotoUpload({
  id,
  label,
  actionLabel,
  emptyStatus,
  file,
  required = false,
  inputRef,
  onChange,
}: Readonly<{
  id: string
  label: string
  actionLabel: string
  emptyStatus: string
  file: File | null
  required?: boolean
  inputRef?: Ref<HTMLInputElement>
  onChange: (event: ChangeEvent<HTMLInputElement>) => void
}>) {
  const labelId = `${id}-label`
  const actionId = `${id}-action`
  const statusId = `${id}-status`
  const helpId = `${id}-help`

  return (
    <div className="mt-field">
      <span id={labelId} className="mt-field-label mt-mono">
        {label}
      </span>
      <div className="mt-file-control">
        <input
          ref={inputRef}
          id={id}
          className="mt-file-input"
          type="file"
          accept={INVENTORY_PHOTO_ACCEPT}
          required={required}
          aria-labelledby={`${labelId} ${actionId}`}
          aria-describedby={`${statusId} ${helpId}`}
          onChange={onChange}
        />
        <label id={actionId} className="mt-file-trigger" htmlFor={id}>
          {actionLabel}
        </label>
        <span id={statusId} className="mt-file-status mt-mono" role="status">
          {file?.name ?? emptyStatus}
        </span>
      </div>
      <span id={helpId} className="mt-inv-file-help">
        JPEG, PNG, or WebP · 5 MB maximum
      </span>
    </div>
  )
}
