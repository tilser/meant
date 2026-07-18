import {
  type ChangeEvent,
  type Dispatch,
  type FormEvent,
  type SetStateAction,
  useEffect,
  useMemo,
  useState,
} from 'react'

import type {
  UserInventoryCategory,
  UserInventoryItemInput,
  UserInventoryItemProfile,
  UserInventoryItemUpdateInput,
  UserInventoryPhotoInput,
} from '../../../lib/apiClient'
import { ProductSearchLoading } from '../chat/ProductSearchLoading'
import { PrefChip } from '../shared/icons'
import { EmptyState, Placeholder, SparkMark, ViewHead } from '../shared/ui'
import {
  type InventoryFormState,
  INVENTORY_CATEGORIES,
  fileToInventoryPhotoUrl,
  initialInventoryForm,
  inventoryCategoryLabel,
  inventoryDateLabel,
  inventoryFormFromItem,
  inventoryItemImage,
  inventoryItemInputFromForm,
  inventoryPhotoInputFromForm,
  inventorySelectedOptionLabel,
  inventorySourceLabel,
  inventoryUpdateInputFromForm,
} from './inventoryUtils'

export function InventoryView({
  items,
  loading,
  error,
  onRefresh,
  onAddItem,
  onAddPhotoItem,
  onUpdateItem,
  onDeleteItem,
  onExport,
}: Readonly<{
  items: readonly UserInventoryItemProfile[]
  loading: boolean
  error: string | null
  onRefresh: () => void
  onAddItem: (input: UserInventoryItemInput) => Promise<UserInventoryItemProfile>
  onAddPhotoItem: (input: UserInventoryPhotoInput) => Promise<UserInventoryItemProfile>
  onUpdateItem: (
    itemId: string,
    input: UserInventoryItemUpdateInput,
  ) => Promise<UserInventoryItemProfile>
  onDeleteItem: (itemId: string) => Promise<void>
  onExport: () => Promise<void>
}>) {
  const [categoryFilter, setCategoryFilter] = useState<UserInventoryCategory | 'ALL'>('ALL')
  const [restockOnly, setRestockOnly] = useState(false)
  const [manualForm, setManualForm] = useState<InventoryFormState>(() => initialInventoryForm())
  const [photoForm, setPhotoForm] = useState<InventoryFormState>(() =>
    initialInventoryForm('OTHER'),
  )
  const [saving, setSaving] = useState<'manual' | 'photo' | null>(null)
  const [photoProcessing, setPhotoProcessing] = useState(false)
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

  const addManual = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!manualForm.name.trim() || saving) {
      return
    }
    setSaving('manual')
    setFormError(null)
    setNotice(null)
    try {
      await onAddItem(inventoryItemInputFromForm(manualForm))
      setManualForm(initialInventoryForm(manualForm.category))
      setNotice('Item added')
    } catch {
      setFormError('Could not add item')
    } finally {
      setSaving(null)
    }
  }

  const addPhoto = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!photoForm.photoUrl.trim() || saving || photoProcessing) {
      return
    }
    setSaving('photo')
    setFormError(null)
    setNotice(null)
    try {
      await onAddPhotoItem(inventoryPhotoInputFromForm(photoForm))
      setPhotoForm(initialInventoryForm('OTHER'))
      setNotice('Photo item added')
    } catch {
      setFormError('Could not add photo item')
    } finally {
      setSaving(null)
    }
  }

  const readPhoto = async (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget
    const file = input.files?.[0]
    if (!file) {
      return
    }
    setPhotoProcessing(true)
    setFormError(null)
    setNotice(null)
    try {
      const photoUrl = await fileToInventoryPhotoUrl(file)
      const fileName = file.name
        .replace(/\.[^.]+$/, '')
        .replace(/[-_]+/g, ' ')
        .trim()
      setPhotoForm((current) => ({
        ...current,
        photoUrl,
        name: current.name || fileName,
      }))
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Could not process photo')
    } finally {
      setPhotoProcessing(false)
      input.value = ''
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
        <InventoryManualFormPanel
          form={manualForm}
          pending={saving === 'manual'}
          onChange={setManualForm}
          onSubmit={addManual}
        />
        <InventoryPhotoFormPanel
          form={photoForm}
          pending={saving === 'photo'}
          processing={photoProcessing}
          onChange={setPhotoForm}
          onPhoto={readPhoto}
          onSubmit={addPhoto}
        />
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
              ? 'Manual entries, photo adds, and Meant purchases will appear here.'
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

function InventoryManualFormPanel({
  form,
  pending,
  onChange,
  onSubmit,
}: Readonly<{
  form: InventoryFormState
  pending: boolean
  onChange: Dispatch<SetStateAction<InventoryFormState>>
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}>) {
  return (
    <form className="mt-inv-panel" onSubmit={onSubmit}>
      <div className="mt-inv-panel-head">
        <h2 className="mt-inv-panel-title">Manual add</h2>
        <button
          className="mt-act mt-act-primary"
          type="submit"
          disabled={!form.name.trim() || pending}
        >
          {pending ? 'Adding' : 'Add item'}
        </button>
      </div>
      <div className="mt-inv-form-grid">
        <InventoryTextField
          label="Name"
          value={form.name}
          onChange={(name) => onChange((current) => ({ ...current, name }))}
          required
        />
        <InventoryTextField
          label="Brand"
          value={form.brand}
          onChange={(brand) => onChange((current) => ({ ...current, brand }))}
        />
        <InventoryCategoryField
          value={form.category}
          onChange={(category) =>
            onChange((current) => ({
              ...current,
              category,
              consumable: category === 'PANTRY' ? true : current.consumable,
            }))
          }
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
      <InventoryTextField
        label="Image URL"
        value={form.imageUrl}
        onChange={(imageUrl) => onChange((current) => ({ ...current, imageUrl }))}
      />
      <InventoryTextField
        label="Product URL"
        value={form.productUrl}
        onChange={(productUrl) => onChange((current) => ({ ...current, productUrl }))}
      />
      <InventoryTextArea
        label="Attributes"
        value={form.attributes}
        onChange={(attributes) => onChange((current) => ({ ...current, attributes }))}
      />
      <InventoryTextArea
        label="Notes"
        value={form.notes}
        onChange={(notes) => onChange((current) => ({ ...current, notes }))}
      />
      <InventoryRestockFields form={form} onChange={onChange} />
    </form>
  )
}

function InventoryPhotoFormPanel({
  form,
  pending,
  processing,
  onChange,
  onPhoto,
  onSubmit,
}: Readonly<{
  form: InventoryFormState
  pending: boolean
  processing: boolean
  onChange: Dispatch<SetStateAction<InventoryFormState>>
  onPhoto: (event: ChangeEvent<HTMLInputElement>) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}>) {
  return (
    <form className="mt-inv-panel" onSubmit={onSubmit}>
      <div className="mt-inv-panel-head">
        <h2 className="mt-inv-panel-title">Photo add</h2>
        <button
          className="mt-act mt-act-primary"
          type="submit"
          disabled={!form.photoUrl.trim() || pending || processing}
        >
          {pending ? 'Adding' : 'Use photo'}
        </button>
      </div>
      <label className="mt-field">
        <span className="mt-field-label mt-mono">Photo</span>
        <input className="mt-file" type="file" accept="image/*" onChange={onPhoto} />
      </label>
      {form.photoUrl ? (
        <div className="mt-inv-photo-preview">
          <img src={form.photoUrl} alt="" />
        </div>
      ) : (
        <div className="mt-inv-photo-empty mt-mono">
          {processing ? 'Processing photo' : 'No photo selected'}
        </div>
      )}
      <div className="mt-inv-form-grid">
        <InventoryTextField
          label="Name"
          value={form.name}
          onChange={(name) => onChange((current) => ({ ...current, name }))}
        />
        <InventoryTextField
          label="Brand"
          value={form.brand}
          onChange={(brand) => onChange((current) => ({ ...current, brand }))}
        />
        <InventoryCategoryField
          value={form.category}
          onChange={(category) =>
            onChange((current) => ({
              ...current,
              category,
              consumable: category === 'PANTRY' ? true : current.consumable,
            }))
          }
        />
        <InventoryTextField
          label="Quantity"
          type="number"
          value={form.quantity}
          onChange={(quantity) => onChange((current) => ({ ...current, quantity }))}
          min="1"
        />
      </div>
      <InventoryTextArea
        label="Notes"
        value={form.notes}
        onChange={(notes) => onChange((current) => ({ ...current, notes }))}
      />
      <InventoryRestockFields form={form} onChange={onChange} />
    </form>
  )
}

function InventoryItemCard({
  item,
  onUpdate,
  onDelete,
}: Readonly<{
  item: UserInventoryItemProfile
  onUpdate: (
    itemId: string,
    input: UserInventoryItemUpdateInput,
  ) => Promise<UserInventoryItemProfile>
  onDelete: (itemId: string) => Promise<void>
}>) {
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<InventoryFormState>(() => inventoryFormFromItem(item))
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const image = inventoryItemImage(item)
  const purchasedAt = inventoryDateLabel(item.purchasedAt)
  const updatedAt = inventoryDateLabel(item.updatedAt)
  const selectedOptionLabels = (item.commerceReference?.selectedOptions ?? [])
    .map(inventorySelectedOptionLabel)
    .filter((label): label is string => Boolean(label))

  useEffect(() => {
    setForm(inventoryFormFromItem(item))
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
      await onUpdate(item.id, inventoryUpdateInputFromForm(form))
      setEditing(false)
    } catch {
      setError('Could not update item')
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    if (!confirmDelete) {
      setConfirmDelete(true)
      return
    }
    setDeleting(true)
    setError(null)
    try {
      await onDelete(item.id)
    } catch {
      setError('Could not delete item')
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
            {item.brand ? <div className="mt-inv-brand">{item.brand}</div> : null}
          </div>
          <div className="mt-inv-actions">
            <button
              className="mt-act mt-act-ghost"
              type="button"
              onClick={() => setEditing((current) => !current)}
            >
              {editing ? 'Cancel' : 'Edit'}
            </button>
            <button
              className="mt-act mt-act-ghost danger"
              type="button"
              onClick={() => void remove()}
              disabled={deleting}
            >
              {confirmDelete ? 'Confirm delete' : deleting ? 'Deleting' : 'Delete'}
            </button>
          </div>
        </div>

        {editing ? (
          <form className="mt-inv-edit" onSubmit={save}>
            <div className="mt-inv-form-grid">
              <InventoryTextField
                label="Name"
                value={form.name}
                onChange={(name) => setForm((current) => ({ ...current, name }))}
                required
              />
              <InventoryTextField
                label="Brand"
                value={form.brand}
                onChange={(brand) => setForm((current) => ({ ...current, brand }))}
              />
              <InventoryCategoryField
                value={form.category}
                onChange={(category) =>
                  setForm((current) => ({
                    ...current,
                    category,
                    consumable: category === 'PANTRY' ? true : current.consumable,
                  }))
                }
              />
              <InventoryTextField
                label="Quantity"
                type="number"
                value={form.quantity}
                onChange={(quantity) => setForm((current) => ({ ...current, quantity }))}
                min="1"
              />
              <InventoryTextField
                label="Unit"
                value={form.unit}
                onChange={(unit) => setForm((current) => ({ ...current, unit }))}
              />
              <InventoryTextField
                label="Location"
                value={form.location}
                onChange={(location) => setForm((current) => ({ ...current, location }))}
              />
            </div>
            <InventoryTextArea
              label="Attributes"
              value={form.attributes}
              onChange={(attributes) => setForm((current) => ({ ...current, attributes }))}
            />
            <InventoryTextArea
              label="Notes"
              value={form.notes}
              onChange={(notes) => setForm((current) => ({ ...current, notes }))}
            />
            <InventoryRestockFields form={form} onChange={setForm} />
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
              {item.location ? <span>{item.location}</span> : null}
              {item.restockEnabled ? (
                <span>
                  Restock{item.restockThreshold !== null ? ` at ${item.restockThreshold}` : ''}
                </span>
              ) : null}
              {purchasedAt ? <span>Bought {purchasedAt}</span> : null}
              {updatedAt ? <span>Updated {updatedAt}</span> : null}
            </div>
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
          </>
        )}
        {error ? <div className="mt-cart-inline-error">{error}</div> : null}
      </div>
    </article>
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
  type?: 'text' | 'number' | 'url'
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
      <span className="mt-field-label mt-mono">Category</span>
      <select
        className="mt-select"
        value={value}
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
