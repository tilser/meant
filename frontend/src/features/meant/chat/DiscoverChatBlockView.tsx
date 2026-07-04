import type { DragEvent as ReactDragEvent } from 'react'

import type {
  CartItem,
  CheckoutPayload,
  Preference,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import { bestOffer, formatOrderDate, money, productPriceFrom } from '../utils'
import { productCuratedTake } from '../product/productCuration'
import { CartIcon, ProductArtwork, SparkMark } from '../shared/ui'
import { DiscoverProductBatch } from './DiscoverProductBatch'
import { InlineCartBlock } from './blocks/InlineCartBlock'
import { InlineCheckoutBlock } from './blocks/InlineCheckoutBlock'
import { InlineMiniCompareBlock } from './blocks/InlineMiniCompareBlock'
import type { DiscoverChatBlock } from './types'
import { cartItemsWithFallback, productsWithFallback, searchProductReviewInsight } from './utils'

export function DiscoverChatBlockView({
  block,
  deliveryLocations,
  preferences,
  cart,
  cartProducts,
  savedSet,
  savePendingSet,
  pinnedSet,
  watchedSet,
  shelfProductSet,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onJustPick,
  onCompareHere,
  onOpenFullCompare,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onReviewCartHere,
  onRestoreCartLine,
  onCartQty,
  onCartRemove,
  onCheckout,
  onCheckoutHere,
  onShelfAddProduct,
  onDragProduct,
}: Readonly<{
  block: DiscoverChatBlock
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfProductSet: ReadonlySet<ProductId>
  onOpen: (product: Product, products?: readonly Product[]) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (kind: 'reviews' | 'code' | 'similar', product: Product) => void
  onJustPick: (products: readonly Product[]) => void
  onCompareHere: (products: readonly Product[]) => void
  onOpenFullCompare: (products: readonly Product[]) => void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onReviewCartHere: (lines?: readonly CartItem[], products?: readonly Product[]) => void
  onRestoreCartLine: (product: Product, merchant: string, price?: number) => void
  onCartQty: (id: ProductId, merchant: string, qty: number, nextCart: readonly CartItem[]) => void
  onCartRemove: (id: ProductId, merchant: string, nextCart: readonly CartItem[]) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onCheckoutHere: () => void
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
}>) {
  if (block.type === 'text') {
    return <p className="mt-ct-intro">{block.text}</p>
  }
  if (block.type === 'system') {
    return (
      <div className="mt-ct-system">
        <SparkMark size={11} color="var(--faint)" />
        {block.text}
      </div>
    )
  }
  if (block.type === 'products') {
    return (
      <DiscoverProductBatch
        products={block.products}
        query={block.query}
        deliveryLocations={deliveryLocations}
        preferences={preferences}
        savedSet={savedSet}
        savePendingSet={savePendingSet}
        pinnedSet={pinnedSet}
        watchedSet={watchedSet}
        shelfProductSet={shelfProductSet}
        onOpen={onOpen}
        onToggleSave={onToggleSave}
        onAddCart={onAddCart}
        onPin={onPin}
        onWatch={onWatch}
        onDig={onDig}
        onJustPick={onJustPick}
        onCompareHere={onCompareHere}
        onShelfAddProduct={onShelfAddProduct}
        onDragProduct={onDragProduct}
      />
    )
  }
  if (block.type === 'reviews') {
    const score = block.product.review.score
    return (
      <div className="mt-ct-block">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Reviews · {block.product.name}</div>
          <div className="mt-reviews-score">
            {score !== null ? (
              <span className="mt-stars">{'★'.repeat(Math.round(score))}</span>
            ) : null}
            <span className="mt-mono">
              {score !== null ? `${score.toFixed(1)} · ` : ''}
              {block.product.review.count.toLocaleString()}
            </span>
          </div>
        </div>
        <p className="mt-ct-review-sum">
          {block.product.review.insight ||
            searchProductReviewInsight(
              block.product.agentStage === 'candidate',
              block.product.review.score,
              block.product.review.count,
            )}
        </p>
      </div>
    )
  }
  if (block.type === 'code') {
    const offer = bestOffer(block.product, deliveryLocations)
    return (
      <div className="mt-ct-block mt-ct-code">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Discount found · {offer.merchant}</div>
          <span className="mt-ct-code-save mt-mono">Mocked code · save {money(block.saved)}</span>
        </div>
        <div className="mt-ct-code-row">
          <span className="mt-code">
            <span className="mt-code-val mt-mono">{block.code}</span>
            <span className="mt-code-act mt-mono">mock</span>
          </span>
          <div className="mt-ct-code-detail">
            <div className="mt-ct-code-label">A mocked coupon agent found this candidate.</div>
            <div className="mt-ct-code-price">
              <span className="mt-ct-code-was">{money(offer.price)}</span>
              <span className="mt-ct-code-now">
                {money(Math.max(0, offer.price - block.saved))}
              </span>
              <span className="mt-mono mt-ct-code-deliv">{offer.delivery}</span>
            </div>
          </div>
          <button
            className="mt-ct-addbtn solid"
            type="button"
            onClick={() => onAddCart(block.product)}
          >
            <CartIcon /> Add
          </button>
        </div>
      </div>
    )
  }
  if (block.type === 'similar') {
    return (
      <div className="mt-ct-block">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Similar to {block.product.name}</div>
        </div>
        <DiscoverProductBatch
          products={block.products}
          deliveryLocations={deliveryLocations}
          preferences={preferences}
          savedSet={savedSet}
          savePendingSet={savePendingSet}
          pinnedSet={pinnedSet}
          watchedSet={watchedSet}
          shelfProductSet={shelfProductSet}
          onOpen={onOpen}
          onToggleSave={onToggleSave}
          onAddCart={onAddCart}
          onPin={onPin}
          onWatch={onWatch}
          onDig={onDig}
          onJustPick={onJustPick}
          onCompareHere={onCompareHere}
          onShelfAddProduct={onShelfAddProduct}
          onDragProduct={onDragProduct}
        />
      </div>
    )
  }
  if (block.type === 'decision') {
    return (
      <div className="mt-ct-decision">
        <div className="mt-ct-decision-head">
          <span className="mt-mono mt-ct-decision-key">
            <SparkMark size={12} /> Meant's pick
          </span>
          <span className="mt-ct-decision-conf">
            {Math.max(76, block.product.match)}% confident
          </span>
        </div>
        <button className="mt-ct-decision-prod" type="button" onClick={() => onOpen(block.product)}>
          <span className="mt-ct-decision-media">
            <ProductArtwork product={block.product} label={block.product.category.toLowerCase()} />
          </span>
          <span className="mt-ct-decision-info">
            <span className="mt-mono mt-ct-decision-brand">{block.product.brand}</span>
            <span className="mt-ct-decision-name">{block.product.name}</span>
            <span className="mt-ct-decision-price">
              {money(productPriceFrom(block.product, deliveryLocations))}
            </span>
          </span>
        </button>
        <p className="mt-ct-decision-why">{productCuratedTake(block.product, preferences)}</p>
        {block.runnerUp ? (
          <div className="mt-ct-decision-beat">
            <span className="mt-mono">vs.</span> Beat {block.runnerUp.name} on match score and fit.
          </div>
        ) : null}
        <div className="mt-ct-decision-actions">
          <button
            className="mt-ct-addbtn solid"
            type="button"
            onClick={() => onAddCart(block.product)}
          >
            <CartIcon /> Add pick
          </button>
        </div>
      </div>
    )
  }
  if (block.type === 'watch') {
    return (
      <div className="mt-ct-watchalert">
        <span className="mt-ct-watchalert-ico">
          <SparkMark size={14} />
        </span>
        <div className="mt-ct-watchalert-body">
          <div className="mt-mono mt-ct-watchalert-key">Mock price watch</div>
          <div className="mt-ct-watchalert-text">
            The <b>{block.product.name}</b> dropped to {money(block.price)} at {block.merchant}.
          </div>
        </div>
        <button
          className="mt-ct-addbtn solid"
          type="button"
          onClick={() => onAddCart(block.product)}
        >
          Add
        </button>
      </div>
    )
  }
  if (block.type === 'friendvote') {
    return (
      <div className="mt-ct-friend">
        <span className="mt-ct-friend-av">{block.person.slice(0, 1)}</span>
        <div className="mt-ct-friend-body">
          <div className="mt-ct-friend-head">
            <span>
              <b>{block.person}</b> weighed in on your pick
            </span>
            <span className={`mt-ct-friend-vote ${block.vote}`}>
              {block.vote === 'up' ? 'Yes, this one' : "I'd skip it"}
            </span>
          </div>
          <p className="mt-ct-friend-quote">"{block.note}"</p>
          <button className="mt-ct-friend-prod" type="button" onClick={() => onOpen(block.product)}>
            <span className="mt-ct-friend-thumb">
              <ProductArtwork
                product={block.product}
                label={block.product.category.toLowerCase()}
              />
            </span>
            <span className="mt-ct-friend-name">{block.product.name}</span>
          </button>
          {block.vote === 'up' ? (
            <button className="mt-ct-addbtn" type="button" onClick={() => onAddCart(block.product)}>
              <CartIcon /> Add their pick
            </button>
          ) : null}
        </div>
      </div>
    )
  }
  if (block.type === 'added') {
    const addedPrice = block.price ?? productPriceFrom(block.product, deliveryLocations)
    const addedCount = block.count ?? cart.reduce((sum, item) => sum + item.qty, 0)
    return (
      <div className="mt-ct-added">
        <span className="mt-ct-added-check">
          <svg width="13" height="13" viewBox="0 0 14 14" aria-hidden>
            <path
              d="M3 7.3l2.6 2.6L11 4.2"
              stroke="currentColor"
              strokeWidth="1.9"
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        <div className="mt-ct-added-body">
          <span className="mt-ct-added-name">
            Added <b>{block.product.name}</b> to your cart
          </span>
          <span className="mt-ct-added-meta">
            {money(addedPrice)} · {block.merchant}
            {block.code ? ` · code ${block.code}` : ''} · {addedCount} in cart
          </span>
        </div>
        <div className="mt-ct-added-actions">
          <button
            className="mt-ct-added-go"
            type="button"
            onClick={() => {
              const liveLine = cart.find(
                (item) => item.id === block.product.id && item.merchant === block.merchant,
              )
              if (!liveLine) {
                onRestoreCartLine(block.product, block.merchant, addedPrice)
              }
              onReviewCartHere(
                [
                  liveLine ?? {
                    id: block.product.id,
                    merchant: block.merchant,
                    qty: 1,
                    productTitle: block.product.name,
                    imageUrl: block.product.imageUrl,
                    unitPriceAmount: String(addedPrice),
                  },
                ],
                [block.product],
              )
            }}
          >
            Review here
          </button>
          <button className="mt-ct-added-go ghost" type="button" onClick={onOpenCart}>
            Open cart
          </button>
        </div>
      </div>
    )
  }
  if (block.type === 'saved') {
    return (
      <div className="mt-ct-block mt-ct-mini2">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Saved items</div>
          <span className="mt-ct-code-save mt-mono">{block.products.length} saved</span>
        </div>
        {block.products.length > 0 ? (
          <div className="mt-ct-mini2-grid">
            {block.products.slice(0, 4).map((product) => (
              <button
                key={product.id}
                className="mt-ct-mini2-card"
                type="button"
                onClick={() => onOpen(product)}
              >
                <span className="mt-ct-mini2-media">
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                </span>
                <span className="mt-ct-mini2-name">{product.name}</span>
                <span className="mt-ct-mini2-price">
                  {money(productPriceFrom(product, deliveryLocations))}
                </span>
              </button>
            ))}
          </div>
        ) : (
          <p className="mt-ct-cart-empty">Nothing saved yet.</p>
        )}
        <button className="mt-ct-mini-full" type="button" onClick={onOpenSaved}>
          Open saved
        </button>
      </div>
    )
  }
  if (block.type === 'orders') {
    return (
      <div className="mt-ct-block mt-ct-mini2">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Recent orders</div>
          <span className="mt-ct-code-save mt-mono">{block.orders.length} total</span>
        </div>
        {block.orders.length > 0 ? (
          <div className="mt-ct-mini2-list">
            {block.orders.slice(0, 3).map((order) => (
              <div className="mt-ct-mini2-row" key={order.id}>
                <div className="mt-ct-mini2-info">
                  <div className="mt-ct-mini2-title">{order.id}</div>
                  <div className="mt-mono mt-ct-mini2-meta">
                    {order.status} · {formatOrderDate(order.date)}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-ct-cart-empty">No orders yet.</p>
        )}
        <button className="mt-ct-mini-full" type="button" onClick={onOpenOrders}>
          Open orders
        </button>
      </div>
    )
  }
  if (block.type === 'prefs') {
    return (
      <div className="mt-ct-block mt-ct-mini2">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Your preferences</div>
          <span className="mt-ct-code-save mt-mono">{block.preferences.length} active</span>
        </div>
        <div className="mt-chips">
          {block.preferences.slice(0, 12).map((preference) => (
            <span key={preference.id} className="mt-chip mt-chip-muted mt-chip-sm in">
              {preference.label}
            </span>
          ))}
        </div>
        <button className="mt-ct-mini-full" type="button" onClick={onOpenPrefs}>
          Edit preferences
        </button>
      </div>
    )
  }
  if (block.type === 'cart') {
    return (
      <InlineCartBlock
        cart={cartItemsWithFallback(cart, block.lines)}
        products={productsWithFallback(block.products, cartProducts)}
        onQty={onCartQty}
        onRemove={onCartRemove}
        onAddCart={onAddCart}
        onOpenCart={onOpenCart}
        onCheckoutHere={onCheckoutHere}
      />
    )
  }
  if (block.type === 'checkout') {
    return (
      <InlineCheckoutBlock
        cart={cart}
        products={cartProducts}
        onCheckout={onCheckout}
        onOpenCart={onOpenCart}
        onOpenOrders={onOpenOrders}
      />
    )
  }
  return (
    <InlineMiniCompareBlock
      block={block}
      deliveryLocations={deliveryLocations}
      onOpen={onOpen}
      onAddCart={onAddCart}
      onOpenFullCompare={onOpenFullCompare}
    />
  )
}
