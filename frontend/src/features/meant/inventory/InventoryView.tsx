import {
  type ChangeEvent,
  type Dispatch,
  type FormEvent,
  type SetStateAction,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import {
  getInventoryPhotoUrl,
  type UserInventoryCategory,
  type UserInventoryItemProfile,
  type UserInventoryItemUpdateInput,
  validateInventoryPhotoFile,
} from '../../../lib/apiClient'
import { merchantAdjacentDisplayLabel } from '../cart/merchantOrigin'
import { ProductSearchLoading } from '../chat/ProductSearchLoading'
import { PrefChip } from '../shared/icons'
import { EmptyState, Placeholder, SparkMark, ViewHead } from '../shared/ui'
import { InventoryPhotoUpload } from './InventoryPhotoUpload'
import {
  type InventoryFormState,
  INVENTORY_CATEGORIES,
  initialInventoryForm,
  inventoryCategoryLabel,
  inventoryDateLabel,
  inventoryFormFromItem,
  inventoryItemImage,
  inventoryItemInputFromForm,
  inventorySelectedOptionLabel,
  inventorySourceLabel,
  inventoryUpdateInputFromForm,
  safeInventoryProductUrl,
  type UserInventoryItemDraftInput,
} from './inventoryUtils'

export function InventoryView({
  userId,
  items,
  loading,
  error,
  onRefresh,
  onAddItem,
  onUpdateItem,
  onDeleteItem,
  onExport,
}: Readonly<{
  userId: string
  items: readonly UserInventoryItemProfile[]
  loading: boolean
  error: string | null
  onRefresh: () => void
  onAddItem: (input: UserInventoryItemDraftInput, photo: File) => Promise<UserInventoryItemProfile>
  onUpdateItem: (
    item: UserInventoryItemProfile,
    input: UserInventoryItemUpdateInput,
    replacementPhoto?: File,
  ) => Promise<UserInventoryItemProfile>
  onDeleteItem: (item: UserInventoryItemProfile) => Promise<void>
  onExport: () => Promise<void>
}>) {
  const [categoryFilter, setCategoryFilter] = useState<UserInventoryCategory | 'ALL'>('ALL')
  const [restockOnly, setRestockOnly] = useState(false)
  const [form, setForm] = useState<InventoryFormState>(() => initialInventoryForm())
  const [photoFile, setPhotoFile] = useState<File | null>(null)
  const photoPreviewUrl = useFilePreview(photoFile)
  const photoInputRef = useRef<HTMLInputElement>(null)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const filteredItems = useMemo(
    () =>
      items.filter(
        (item) =>
          (categoryFilter === 'ALL' || item.category === categoryFilter) &&
          (!restockOnly || item.restockEnabled),
      ),
    [categoryFilter, items, restockOnly],
  )
  const pantryCount = items.filter((item) => item.category === 'PANTRY').length
  const restockCount = items.filter((item) => item.restockEnabled).length
  const purchasedCount = items.filter((item) => item.source === 'MEANT_PURCHASE').length

  const addItem = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!form.name.trim() || !photoFile || saving) {
      return
    }
    setSaving(true)
    setFormError(null)
    setNotice(null)
    try {
      await onAddItem(inventoryItemInputFromForm(form), photoFile)
      setForm(initialInventoryForm(form.category))
      setPhotoFile(null)
      if (photoInputRef.current) {
        photoInputRef.current.value = ''
      }
      setNotice('Item added')
    } catch (addError) {
      setFormError(addError instanceof Error ? addError.message : 'Could not add item')
    } finally {
      setSaving(false)
    }
  }

  const selectPhoto = (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget
    const file = input.files?.[0]
    if (!file) {
      setPhotoFile(null)
      return
    }
    setFormError(null)
    setNotice(null)
    try {
      validateInventoryPhotoFile(file)
      setPhotoFile(file)
    } catch (photoError) {
      setPhotoFile(null)
      input.value = ''
      setFormError(photoError instanceof Error ? photoError.message : 'Could not use photo')
    }
  }

  const exportItems = async () => {
    if (exporting) {
      return
    }
    setExporting(true)
    setFormError(null)
    setNotice(null)
    try {
      await onExport()
      setNotice('Inventory exported')
    } catch {
      setFormError('Could not export inventory')
    } finally {
      setExporting(false)
    }
  }

  return (
    <main className="mt-feed mt-view mt-inventory-view">
      <ViewHead
        eyebrow="Owned items"
        title="Inventory"
        right={
          <div className="mt-inv-head-actions">
            <button
              className="mt-empty-btn ghost"
              type="button"
              onClick={onRefresh}
              disabled={loading}
            >
              Refresh
            </button>
            <button
              className="mt-empty-btn"
              type="button"
              onClick={() => void exportItems()}
              disabled={exporting}
            >
              {exporting ? 'Exporting' : 'Export data'}
            </button>
          </div>
        }
      />

      <div className="mt-inv-stats">
        <InventoryStat label="Items" value={items.length} />
        <InventoryStat label="Restock" value={restockCount} />
        <InventoryStat label="Pantry" value={pantryCount} />
        <InventoryStat label="Purchases" value={purchasedCount} />
      </div>

      <div className="mt-inv-workbench">
        <form className="mt-inv-panel" onSubmit={addItem}>
          <div className="mt-inv-panel-head">
            <div>
              <h2 className="mt-inv-panel-title">Add item</h2>
              <p className="mt-inv-panel-sub">Add something you already own.</p>
            </div>
            <button
              className="mt-act mt-act-primary"
              type="submit"
              disabled={!photoFile || !form.name.trim() || saving}
            >
              {saving ? 'Adding' : 'Add item'}
            </button>
          </div>

          <div className="mt-inv-create-core">
            <div>
              <InventoryPhotoUpload
                id="inventory-add-photo"
                label="Photo *"
                actionLabel="Choose photo"
                emptyStatus="No photo selected"
                file={photoFile}
                required
                inputRef={photoInputRef}
                onChange={selectPhoto}
              />
              {photoPreviewUrl ? (
                <div className="mt-inv-photo-preview">
                  <img src={photoPreviewUrl} alt="Selected inventory item" />
                </div>
              ) : (
                <div className="mt-inv-photo-empty mt-mono">No photo selected</div>
              )}
            </div>
            <div>
              <div className="mt-inv-form-grid">
                <InventoryTextField
                  label="Name *"
                  value={form.name}
                  onChange={(name) => setForm((current) => ({ ...current, name }))}
                  required
                />
                <InventoryCategoryField
                  value={form.category}
                  onChange={(category) => changeCategory(setForm, category)}
                />
              </div>
              <InventoryOptionalFields form={form} onChange={setForm} />
            </div>
          </div>
          <InventoryMoreDetails form={form} onChange={setForm} />
        </form>
      </div>

      {error ? <div className="mt-search-state mt-search-state-error">{error}</div> : null}
      {formError ? <div className="mt-search-state mt-search-state-error">{formError}</div> : null}
      {notice ? <div className="mt-inv-notice mt-mono">{notice}</div> : null}

      <div className="mt-inv-list-head">
        <div className="mt-inv-tabs" role="group" aria-label="Inventory category filter">
          <button
            className={`mt-inv-tab ${categoryFilter === 'ALL' ? 'on' : ''}`}
            type="button"
            onClick={() => setCategoryFilter('ALL')}
          >
            All
          </button>
          {INVENTORY_CATEGORIES.map((category) => (
            <button
              key={category}
              className={`mt-inv-tab ${categoryFilter === category ? 'on' : ''}`}
              type="button"
              onClick={() => setCategoryFilter(category)}
            >
              {inventoryCategoryLabel(category)}
            </button>
          ))}
        </div>
        <label className="mt-inv-check">
          <input
            type="checkbox"
            checked={restockOnly}
            onChange={(event) => setRestockOnly(event.target.checked)}
          />
          <span>Restock only</span>
        </label>
      </div>

      {loading && items.length === 0 ? (
        <ProductSearchLoading label="Loading inventory" />
      ) : filteredItems.length > 0 ? (
        <div className="mt-inv-list">
          {filteredItems.map((item) => (
            <InventoryItemCard
              key={item.id}
              userId={userId}
              item={item}
              onUpdate={onUpdateItem}
              onDelete={onDeleteItem}
            />
          ))}
        </div>
      ) : (
        <EmptyState
          title={items.length === 0 ? 'No owned items yet' : 'No items match this filter'}
          sub={
            items.length === 0
              ? 'Items you add and purchases made through Meant will appear here.'
              : 'Change the category or restock filter.'
          }
          mark={<SparkMark />}
        />
      )}
    </main>
  )
}

function InventoryStat({ label, value }: Readonly<{ label: string; value: number }>) {
  return (
    <div className="mt-inv-stat">
      <span className="mt-mono mt-inv-stat-label">{label}</span>
      <span className="mt-inv-stat-value">{value}</span>
    </div>
  )
}

function InventoryItemCard({
  userId,
  item,
  onUpdate,
  onDelete,
}: Readonly<{
  userId: string
  item: UserInventoryItemProfile
  onUpdate: (
    item: UserInventoryItemProfile,
    input: UserInventoryItemUpdateInput,
    replacementPhoto?: File,
  ) => Promise<UserInventoryItemProfile>
  onDelete: (item: UserInventoryItemProfile) => Promise<void>
}>) {
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<InventoryFormState>(() => inventoryFormFromItem(item))
  const [replacementPhoto, setReplacementPhoto] = useState<File | null>(null)
  const replacementPreviewUrl = useFilePreview(replacementPhoto)
  const signedPhotoUrl = useInventoryPhotoUrl(item.photoPath, userId)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const image = inventoryItemImage(item, signedPhotoUrl)
  const purchaseDate = inventoryDateLabel(item.purchasedOn ?? item.purchasedAt)
  const updatedAt = inventoryDateLabel(item.updatedAt)
  const productUrl = safeInventoryProductUrl(
    item.productUrl,
    item.commerceReference?.merchantOrigin,
  )
  const selectedOptionLabels = (item.commerceReference?.selectedOptions ?? [])
    .map(inventorySelectedOptionLabel)
    .filter((label): label is string => Boolean(label))

  useEffect(() => {
    setForm(inventoryFormFromItem(item))
    setReplacementPhoto(null)
    setEditing(false)
    setConfirmDelete(false)
    setError(null)
  }, [item])

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!form.name.trim() || saving) {
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onUpdate(item, inventoryUpdateInputFromForm(form), replacementPhoto ?? undefined)
      setReplacementPhoto(null)
      setEditing(false)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Could not update item')
    } finally {
      setSaving(false)
    }
  }

  const selectReplacementPhoto = (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget
    const file = input.files?.[0]
    if (!file) {
      setReplacementPhoto(null)
      return
    }
    setError(null)
    try {
      validateInventoryPhotoFile(file)
      setReplacementPhoto(file)
    } catch (photoError) {
      setReplacementPhoto(null)
      input.value = ''
      setError(photoError instanceof Error ? photoError.message : 'Could not use photo')
    }
  }

  const toggleEditing = () => {
    if (editing) {
      setForm(inventoryFormFromItem(item))
      setReplacementPhoto(null)
      setError(null)
    }
    setEditing((current) => !current)
  }

  const remove = async () => {
    if (!confirmDelete) {
      setConfirmDelete(true)
      return
    }
    setDeleting(true)
    setError(null)
    try {
      await onDelete(item)
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Could not delete item')
      setDeleting(false)
      setConfirmDelete(false)
    }
  }

  return (
    <article className={`mt-inv-item ${editing ? 'editing' : ''}`}>
      <div className="mt-inv-media">
        {image ? (
          <img src={image} alt="" loading="lazy" />
        ) : (
          <Placeholder label={inventoryCategoryLabel(item.category)} tone="#e7ebef" />
        )}
      </div>
      <div className="mt-inv-main">
        <div className="mt-inv-item-top">
          <div>
            <div className="mt-mono mt-inv-source">
              {inventorySourceLabel(item.source)} · {inventoryCategoryLabel(item.category)}
            </div>
            <h3 className="mt-inv-name">{item.name}</h3>
            {item.brand ? (
              <div className="mt-inv-brand">
                {merchantAdjacentDisplayLabel(item.brand, item.commerceReference?.merchantOrigin)}
              </div>
            ) : null}
          </div>
          <div className="mt-inv-actions">
            <button className="mt-act mt-act-ghost" type="button" onClick={toggleEditing}>
              {editing ? 'Cancel' : 'Edit'}
            </button>
            <button
              className="mt-act mt-act-ghost danger"
              type="button"
              onClick={() => void remove()}
              disabled={deleting}
            >
              {deleting ? 'Deleting' : confirmDelete ? 'Confirm delete' : 'Delete'}
            </button>
          </div>
        </div>

        {editing ? (
          <form className="mt-inv-edit" onSubmit={save}>
            <div className="mt-inv-edit-photo">
              <div className="mt-inv-photo-preview">
                {replacementPreviewUrl || image ? (
                  <img src={replacementPreviewUrl || image || ''} alt="Inventory item" />
                ) : (
                  <Placeholder label={inventoryCategoryLabel(item.category)} tone="#e7ebef" />
                )}
              </div>
              <InventoryPhotoUpload
                id={`inventory-${item.id}-replacement-photo`}
                label="Replace photo"
                actionLabel="Choose replacement"
                emptyStatus="Current photo retained"
                file={replacementPhoto}
                onChange={selectReplacementPhoto}
              />
            </div>
            <div className="mt-inv-form-grid">
              <InventoryTextField
                label="Name *"
                value={form.name}
                onChange={(name) => setForm((current) => ({ ...current, name }))}
                required
              />
              <InventoryCategoryField
                value={form.category}
                onChange={(category) => changeCategory(setForm, category)}
              />
            </div>
            <InventoryOptionalFields form={form} onChange={setForm} />
            <InventoryMoreDetails form={form} onChange={setForm} />
            <div className="mt-inv-save-row">
              <button
                className="mt-act mt-act-primary"
                type="submit"
                disabled={!form.name.trim() || saving}
              >
                {saving ? 'Saving' : 'Save changes'}
              </button>
            </div>
          </form>
        ) : (
          <>
            <div className="mt-inv-meta">
              <span>
                {item.quantity}
                {item.unit ? ` ${item.unit}` : ''}
              </span>
              {item.size ? <span>Size {item.size}</span> : null}
              {item.color ? <span>{item.color}</span> : null}
              {item.material ? <span>{item.material}</span> : null}
              {item.location ? <span>{item.location}</span> : null}
              {item.restockEnabled ? (
                <span>
                  Restock{item.restockThreshold !== null ? ` at ${item.restockThreshold}` : ''}
                </span>
              ) : null}
              {purchaseDate ? <span>Bought {purchaseDate}</span> : null}
              {updatedAt ? <span>Updated {updatedAt}</span> : null}
            </div>
            {item.description ? <p className="mt-inv-notes">{item.description}</p> : null}
            {item.attributes.length > 0 ? (
              <div className="mt-chips mt-inv-attrs">
                {item.attributes.map((attribute) => (
                  <PrefChip key={attribute} label={attribute} variant="muted" small />
                ))}
              </div>
            ) : null}
            {selectedOptionLabels.length > 0 ? (
              <div className="mt-chips mt-inv-attrs" aria-label="Purchased options">
                {selectedOptionLabels.map((label) => (
                  <PrefChip key={label} label={label} variant="muted" small />
                ))}
              </div>
            ) : null}
            {item.notes ? <p className="mt-inv-notes">{item.notes}</p> : null}
            {productUrl ? (
              <a className="mt-inv-product-link" href={productUrl} target="_blank" rel="noreferrer">
                Original product
              </a>
            ) : null}
          </>
        )}
        {error ? <div className="mt-cart-inline-error">{error}</div> : null}
      </div>
    </article>
  )
}

function InventoryOptionalFields({
  form,
  onChange,
}: Readonly<{
  form: InventoryFormState
  onChange: Dispatch<SetStateAction<InventoryFormState>>
}>) {
  return (
    <>
      <div className="mt-inv-form-grid">
        <InventoryTextField
          label="Brand"
          value={form.brand}
          onChange={(brand) => onChange((current) => ({ ...current, brand }))}
        />
        <InventoryTextField
          label="Purchase date"
          type="date"
          value={form.purchasedOn}
          onChange={(purchasedOn) => onChange((current) => ({ ...current, purchasedOn }))}
        />
        <InventoryTextField
          label="Size"
          value={form.size}
          onChange={(size) => onChange((current) => ({ ...current, size }))}
        />
        <InventoryTextField
          label="Color"
          value={form.color}
          onChange={(color) => onChange((current) => ({ ...current, color }))}
        />
        <InventoryTextField
          label="Material"
          value={form.material}
          onChange={(material) => onChange((current) => ({ ...current, material }))}
        />
        <InventoryTextField
          label="Quantity"
          type="number"
          value={form.quantity}
          onChange={(quantity) => onChange((current) => ({ ...current, quantity }))}
          min="1"
        />
        <InventoryTextField
          label="Unit"
          value={form.unit}
          onChange={(unit) => onChange((current) => ({ ...current, unit }))}
        />
        <InventoryTextField
          label="Location"
          value={form.location}
          onChange={(location) => onChange((current) => ({ ...current, location }))}
        />
      </div>
      <InventoryTextArea
        label="Description"
        value={form.description}
        onChange={(description) => onChange((current) => ({ ...current, description }))}
      />
      <InventoryTextArea
        label="Notes"
        value={form.notes}
        onChange={(notes) => onChange((current) => ({ ...current, notes }))}
      />
    </>
  )
}

function InventoryMoreDetails({
  form,
  onChange,
}: Readonly<{
  form: InventoryFormState
  onChange: Dispatch<SetStateAction<InventoryFormState>>
}>) {
  return (
    <details className="mt-inv-more">
      <summary>More details</summary>
      <div className="mt-inv-more-body">
        <InventoryTextField
          label="Original product link"
          type="url"
          value={form.productUrl}
          onChange={(productUrl) => onChange((current) => ({ ...current, productUrl }))}
        />
        <InventoryTextArea
          label="Other details or tags"
          value={form.attributes}
          onChange={(attributes) => onChange((current) => ({ ...current, attributes }))}
        />
        <InventoryRestockFields form={form} onChange={onChange} />
      </div>
    </details>
  )
}

function InventoryTextField({
  label,
  value,
  onChange,
  type = 'text',
  required = false,
  min,
}: Readonly<{
  label: string
  value: string
  onChange: (value: string) => void
  type?: 'text' | 'number' | 'url' | 'date'
  required?: boolean
  min?: string
}>) {
  return (
    <label className="mt-field">
      <span className="mt-field-label mt-mono">{label}</span>
      <input
        className="mt-input"
        type={type}
        value={value}
        min={min}
        required={required}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

function InventoryTextArea({
  label,
  value,
  onChange,
}: Readonly<{
  label: string
  value: string
  onChange: (value: string) => void
}>) {
  return (
    <label className="mt-field">
      <span className="mt-field-label mt-mono">{label}</span>
      <textarea
        className="mt-input mt-textarea"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

function InventoryCategoryField({
  value,
  onChange,
}: Readonly<{
  value: UserInventoryCategory
  onChange: (value: UserInventoryCategory) => void
}>) {
  return (
    <label className="mt-field">
      <span className="mt-field-label mt-mono">Category *</span>
      <select
        className="mt-select"
        value={value}
        required
        onChange={(event) => onChange(event.target.value as UserInventoryCategory)}
      >
        {INVENTORY_CATEGORIES.map((category) => (
          <option key={category} value={category}>
            {inventoryCategoryLabel(category)}
          </option>
        ))}
      </select>
    </label>
  )
}

function InventoryRestockFields({
  form,
  onChange,
}: Readonly<{
  form: InventoryFormState
  onChange: Dispatch<SetStateAction<InventoryFormState>>
}>) {
  return (
    <div className="mt-inv-restock-controls">
      <label className="mt-inv-check">
        <input
          type="checkbox"
          checked={form.consumable}
          onChange={(event) =>
            onChange((current) => ({ ...current, consumable: event.target.checked }))
          }
        />
        <span>Consumable</span>
      </label>
      <label className="mt-inv-check">
        <input
          type="checkbox"
          checked={form.restockEnabled}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              restockEnabled: event.target.checked,
              consumable: event.target.checked ? true : current.consumable,
            }))
          }
        />
        <span>Restock</span>
      </label>
      <label className="mt-field mt-inv-threshold">
        <span className="mt-field-label mt-mono">Threshold</span>
        <input
          className="mt-input"
          type="number"
          min="0"
          value={form.restockThreshold}
          disabled={!form.restockEnabled}
          onChange={(event) =>
            onChange((current) => ({ ...current, restockThreshold: event.target.value }))
          }
        />
      </label>
    </div>
  )
}

function changeCategory(
  onChange: Dispatch<SetStateAction<InventoryFormState>>,
  category: UserInventoryCategory,
): void {
  onChange((current) => ({
    ...current,
    category,
    consumable: category === 'PANTRY' ? true : current.consumable,
  }))
}

function useFilePreview(file: File | null): string | null {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null)
      return
    }
    const nextUrl = URL.createObjectURL(file)
    setPreviewUrl(nextUrl)
    return () => URL.revokeObjectURL(nextUrl)
  }, [file])

  return previewUrl
}

function useInventoryPhotoUrl(photoPath: string | null, userId: string): string | null {
  const [signedUrl, setSignedUrl] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setSignedUrl(null)
    if (!photoPath) {
      return () => {
        active = false
      }
    }
    void getInventoryPhotoUrl(photoPath, { expectedUserId: userId })
      .then((url) => {
        if (active) {
          setSignedUrl(url)
        }
      })
      .catch(() => {
        if (active) {
          setSignedUrl(null)
        }
      })
    return () => {
      active = false
    }
  }, [photoPath, userId])

  return signedUrl
}
