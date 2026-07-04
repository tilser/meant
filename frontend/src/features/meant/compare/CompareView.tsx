import { useState } from 'react'

import { productCuratedFields } from '../product/productCuration'
import { MatchRing } from '../shared/icons'
import { CloseIcon, ProductArtwork, ViewHead } from '../shared/ui'
import type { CorePreferenceId, Preference, Product, ProductId, UserLocation } from '../types'
import { bestOffer, money, prefLabel, productMerchantCount, productPriceFrom } from '../utils'

export function CompareView({
  products,
  savedProducts,
  compareIds,
  preferences,
  deliveryLocations,
  onRemove,
  onAdd,
  onOpen,
}: Readonly<{
  products: readonly Product[]
  savedProducts: readonly Product[]
  compareIds: readonly ProductId[]
  preferences: readonly Preference[]
  deliveryLocations: readonly UserLocation[]
  onRemove: (index: number) => void
  onAdd: (product: Product) => void
  onOpen: (product: Product, products: readonly Product[]) => void
}>) {
  const items = compareIds
    .map((id) => products.find((product) => product.id === id))
    .filter((product): product is Product => Boolean(product))
  const selectedIds = items.map((product) => product.id)
  const showAdd = items.length < 4
  const enough = items.length >= 2
  const compareColumnCount = items.length + (showAdd ? 1 : 0)
  const gridStyle = {
    gridTemplateColumns: `180px repeat(${compareColumnCount}, minmax(180px, 240px))`,
  }
  const bestMatch = enough ? Math.max(...items.map((product) => product.match)) : null
  const bestPrice = enough
    ? Math.min(...items.map((product) => productPriceFrom(product, deliveryLocations)))
    : null
  const bestMerchantCount = enough
    ? Math.max(...items.map((product) => productMerchantCount(product, deliveryLocations)))
    : null
  const winner = enough
    ? [...items].sort(
        (left, right) =>
          right.match - left.match ||
          productPriceFrom(left, deliveryLocations) - productPriceFrom(right, deliveryLocations),
      )[0]
    : null
  const comparisonPreferenceIds = preferences
    .map((preference) => preference.id)
    .filter((id) =>
      items.some((product) => product.satisfies.includes(id) || product.misses.includes(id)),
    )
  const meantTake = (product: Product) => productCuratedFields(product, preferences).note

  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Side by side"
        title="Compare"
        sub="Compare up to four products at once. Add products from detail pages or add saved products here, then Meant lines them up against everything you care about."
      />
      {winner ? (
        <div className="mt-cmp-verdict">
          <div>
            <div className="mt-mono mt-cmp-verdict-key">Meant pick</div>
            <div className="mt-cmp-verdict-title">{winner.name}</div>
          </div>
          <p>{meantTake(winner)}</p>
        </div>
      ) : null}
      <div className="mt-cmp">
        <div className="mt-cmp-grid mt-cmp-headrow" style={gridStyle}>
          <div className="mt-cmp-rowlabel mt-cmp-corner mt-mono">{items.length} of 4</div>
          {items.map((product, index) => (
            <CompareSlot
              key={product.id}
              index={index}
              product={product}
              canRemove
              onRemove={onRemove}
              onOpen={(item) => onOpen(item, items)}
            />
          ))}
          {showAdd ? (
            <CompareAddSlot products={savedProducts} selectedIds={selectedIds} onAdd={onAdd} />
          ) : null}
        </div>
        {enough ? (
          <>
            <CompareMetricRow
              label="Match"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: `${product.match}%`,
                win: bestMatch !== null && product.match === bestMatch,
              }))}
              addSpacer={showAdd}
            />
            <CompareMetricRow
              label="Price from"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: money(productPriceFrom(product, deliveryLocations)),
                win:
                  bestPrice !== null && productPriceFrom(product, deliveryLocations) === bestPrice,
              }))}
              addSpacer={showAdd}
            />
            <CompareMetricRow
              label="Stores"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: `${productMerchantCount(product, deliveryLocations)}`,
                win:
                  bestMerchantCount !== null &&
                  productMerchantCount(product, deliveryLocations) === bestMerchantCount,
              }))}
              addSpacer={showAdd}
            />
            <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Reviews</div>
              {items.map((product) => (
                <div key={product.id} className="mt-cmp-cell">
                  {product.review.count > 0 ? (
                    <>
                      {product.review.score !== null ? (
                        <span className="mt-stars">
                          {'★'.repeat(Math.round(product.review.score))}
                        </span>
                      ) : null}
                      <span className="mt-mono mt-cmp-sub">
                        {product.review.score !== null
                          ? `${product.review.score.toFixed(1)} · `
                          : ''}
                        {product.review.count.toLocaleString()}
                        {product.review.score === null ? ' reviews' : ''}
                      </span>
                    </>
                  ) : (
                    <span className="mt-mono mt-cmp-sub">No review data</span>
                  )}
                </div>
              ))}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
            <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Meant take</div>
              {items.map((product) => (
                <div key={product.id} className="mt-cmp-cell">
                  <span className="mt-cmp-text">{meantTake(product)}</span>
                </div>
              ))}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
            <div className="mt-cmp-grid mt-cmp-section" style={gridStyle}>
              <div className="mt-cmp-rowlabel mt-cmp-seclabel mt-mono">Your preferences</div>
              {items.map((product) => (
                <div key={product.id} />
              ))}
              {showAdd ? <div /> : null}
            </div>
            {comparisonPreferenceIds.map((id) => (
              <div className="mt-cmp-grid mt-cmp-row" key={id} style={gridStyle}>
                <div className="mt-cmp-rowlabel">{prefLabel(preferences, id)}</div>
                {items.map((product) => (
                  <div key={product.id} className="mt-cmp-cell">
                    <CompareMark product={product} preferenceId={id} />
                  </div>
                ))}
                {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
              </div>
            ))}
            <div className="mt-cmp-grid mt-cmp-row mt-cmp-last" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Best price at</div>
              {items.map((product) => {
                const offer = bestOffer(product, deliveryLocations)
                return (
                  <div key={product.id} className="mt-cmp-cell">
                    <span className="mt-cmp-store">{offer.merchant}</span>
                    <span className="mt-mono mt-cmp-sub">
                      {money(offer.price)} · {offer.delivery}
                    </span>
                  </div>
                )
              })}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
          </>
        ) : (
          <div className="mt-cmp-hint-row">
            Add at least two products to see them compared field by field.
          </div>
        )}
      </div>
      <p className="mt-cmp-footnote">
        Add products to Compare from a product detail page with Add to compare. Once a product is in
        compare, In compare opens this page. The Add a saved product control only lists products you
        have saved.
      </p>
    </main>
  )
}

function CompareMetricRow({
  label,
  gridStyle,
  cells,
  addSpacer,
}: Readonly<{
  label: string
  gridStyle: { gridTemplateColumns: string }
  cells: readonly { key: string; value: string; win: boolean }[]
  addSpacer: boolean
}>) {
  return (
    <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
      <div className="mt-cmp-rowlabel">{label}</div>
      {cells.map((cell) => (
        <div key={cell.key} className={`mt-cmp-cell ${cell.win ? 'win' : ''}`}>
          <span className="mt-cmp-big">{cell.value}</span>
        </div>
      ))}
      {addSpacer ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
    </div>
  )
}

function CompareMark({
  product,
  preferenceId,
}: Readonly<{
  product: Product
  preferenceId: CorePreferenceId
}>) {
  if (product.satisfies.includes(preferenceId)) {
    return <span className="mt-cmp-mark yes">yes</span>
  }
  if (product.misses.includes(preferenceId)) {
    return <span className="mt-cmp-mark no">no</span>
  }
  return <span className="mt-cmp-mark na">-</span>
}

function CompareSlot({
  index,
  product,
  canRemove,
  onRemove,
  onOpen,
}: Readonly<{
  index: number
  product: Product
  canRemove: boolean
  onRemove: (index: number) => void
  onOpen: (product: Product) => void
}>) {
  return (
    <div className="mt-cmp-col">
      <div className="mt-cmp-media">
        <button
          className="mt-cmp-media-open"
          type="button"
          onClick={() => onOpen(product)}
          aria-label={`Open ${product.name}`}
        >
          <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
          <div className="mt-cmp-ring">
            <MatchRing value={product.match} size={48} stroke={3} />
          </div>
        </button>
        {canRemove ? (
          <button
            className="mt-cmp-remove"
            type="button"
            onClick={() => onRemove(index)}
            aria-label="Remove from comparison"
          >
            <CloseIcon size={12} />
          </button>
        ) : null}
      </div>
      <div className="mt-mono mt-card-brand">{product.brand}</div>
      <div className="mt-cmp-name">{product.name}</div>
    </div>
  )
}

function CompareAddSlot({
  products,
  selectedIds,
  onAdd,
}: Readonly<{
  products: readonly Product[]
  selectedIds: readonly ProductId[]
  onAdd: (product: Product) => void
}>) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-cmp-col mt-cmp-col-empty">
      <div className="mt-cmp-picker">
        <button className="mt-cmp-choose" type="button" onClick={() => setOpen((value) => !value)}>
          <span className="mt-cmp-plus">+</span>
          <span className="mt-cmp-choose-text">Add a saved product</span>
        </button>
        {open ? (
          <CompareMenu
            products={products}
            selectedIds={selectedIds}
            onChoose={(product) => {
              onAdd(product)
              setOpen(false)
            }}
          />
        ) : null}
      </div>
    </div>
  )
}

function CompareMenu({
  products,
  selectedIds,
  onChoose,
}: Readonly<{
  products: readonly Product[]
  selectedIds: readonly ProductId[]
  onChoose: (product: Product) => void
}>) {
  const options = products.filter((product) => !selectedIds.includes(product.id))
  return (
    <div className="mt-cmp-menu">
      {options.length === 0 ? (
        <div className="mt-cmp-menu-empty">No saved products available to add.</div>
      ) : null}
      {options.map((product) => (
        <button
          key={product.id}
          className="mt-cmp-opt"
          type="button"
          onClick={() => onChoose(product)}
        >
          <span className="mt-cmp-opt-sw" style={{ background: product.tone }} />
          <span className="mt-cmp-opt-main">
            <span className="mt-cmp-opt-name">{product.name}</span>
            <span className="mt-mono mt-cmp-opt-cat">{product.category}</span>
          </span>
          <span className="mt-mono mt-cmp-opt-match">{product.match}%</span>
        </button>
      ))}
    </div>
  )
}
