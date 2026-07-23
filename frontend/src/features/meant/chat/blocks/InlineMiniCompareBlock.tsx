import { type CSSProperties, Fragment } from 'react'

import { CartIcon, ProductArtwork } from '../../shared/ui'
import type { Product, UserLocation } from '../../types'
import { money, productPriceFrom } from '../../utils'
import type { DiscoverChatBlock } from '../types'

export function InlineMiniCompareBlock({
  block,
  deliveryLocations,
  onOpen,
  onAddCart,
  onOpenFullCompare,
  agentActionsDisabled = false,
}: Readonly<{
  block: Extract<DiscoverChatBlock, { type: 'minicompare' }>
  deliveryLocations: readonly UserLocation[]
  onOpen: (product: Product, products?: readonly Product[]) => void
  onAddCart: (product: Product) => void
  onOpenFullCompare: (products: readonly Product[]) => void
  agentActionsDisabled?: boolean
}>) {
  const pick = block.products[block.pickIndex] ?? block.products[0]
  const gridStyle: CSSProperties = {
    gridTemplateColumns: `92px repeat(${block.products.length}, minmax(0, 1fr))`,
  }

  return (
    <div className="mt-ct-block mt-ct-mini">
      <div className="mt-ct-block-head">
        <div className="mt-mono mt-ct-block-key">Inline compare</div>
        <span className="mt-ct-code-save mt-mono">{block.products.length} pinned</span>
      </div>
      <div className="mt-ct-mini-grid" style={gridStyle}>
        <span className="mt-ct-mini-axis" />
        {block.products.map((product, index) => (
          <button
            key={product.id}
            className={`mt-ct-mini-prod ${index === block.pickIndex ? 'best' : ''}`}
            type="button"
            onClick={() => onOpen(product, block.products)}
          >
            <span className="mt-ct-mini-thumb">
              <ProductArtwork product={product} label={product.category.toLowerCase()} />
            </span>
            <span className="mt-ct-mini-name">{product.name}</span>
            <span className="mt-mono mt-ct-mini-price">
              {money(productPriceFrom(product, deliveryLocations))}
            </span>
          </button>
        ))}
        {block.rows.map((row) => (
          <Fragment key={row.label}>
            <span className="mt-mono mt-ct-mini-label">{row.label}</span>
            {row.values.map((value, index) => (
              <span
                key={`${row.label}-${block.products[index]?.id ?? index}`}
                className={`mt-ct-mini-cell ${row.winnerIndex === index ? 'win' : ''}`}
              >
                {value}
              </span>
            ))}
          </Fragment>
        ))}
      </div>
      {pick ? (
        <div className="mt-ct-mini-pick">
          <span>
            <b>{pick.name}</b> wins this quick pass on match, price, reviews, and fit gaps.
          </span>
          <button
            className="mt-ct-addbtn solid"
            type="button"
            disabled={agentActionsDisabled}
            onClick={() => onAddCart(pick)}
          >
            <CartIcon /> Add pick
          </button>
        </div>
      ) : null}
      <button
        className="mt-ct-mini-full"
        type="button"
        disabled={agentActionsDisabled}
        onClick={() => onOpenFullCompare(block.products)}
      >
        Open full compare
      </button>
    </div>
  )
}
