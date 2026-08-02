import type { DragEvent as ReactDragEvent } from 'react'

import type {
  CartItem,
  CheckoutPayload,
  Preference,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import type {
  ActiveCheckoutSession,
  CheckoutAssistantHandler,
  CheckoutReleaseHandler,
} from '../cart/checkoutTypes'
import { merchantAdjacentDisplayLabel } from '../cart/merchantOrigin'
import { bestOffer, formatOrderDate, money, productPriceFrom } from '../utils'
import { productCuratedTake } from '../product/productCuration'
import { ProductReviewsPanel } from '../product/ProductReviewsPanel'
import { CopyIcon } from '../shared/icons'
import { CartIcon, ProductArtwork, SparkMark } from '../shared/ui'
import { DiscoverProductBatch } from './DiscoverProductBatch'
import { AgentMarkdown } from './AgentMarkdown'
import { InlineCartBlock } from './blocks/InlineCartBlock'
import { InlineCheckoutBlock } from './blocks/InlineCheckoutBlock'
import { InlineMiniCompareBlock } from './blocks/InlineMiniCompareBlock'
import { ComingSoonNewsletter } from './ComingSoonNewsletter'
import type { DiscoverChatBlock, VisibleProductContextChange } from './types'
import {
  cartItemsForChatBlock,
  cartLineForAddedBlock,
  copyTextToClipboard,
  productOpenWithResearchQuery,
  productsWithFallback,
} from './utils'

function safeDiscountCodeSourceUrl(sourceUrl: string | null | undefined): string | null {
  if (!sourceUrl) {
    return null
  }
  try {
    const url = new URL(sourceUrl)
    const host = url.hostname.toLocaleLowerCase()
    const path = url.pathname.replace(/\/+$/, '') || '/'
    const protocolPath = [
      '/.well-known/ucp.json',
      '/.well-known/ucp',
      '/api/ucp/mcp',
      '/api/mcp',
      '/mcp',
    ].some((value) => path === value || path.startsWith(`${value}/`))
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      host.startsWith('mcp.') ||
      host.includes('.mcp.') ||
      host === 'myshopify.com' ||
      host.endsWith('.myshopify.com') ||
      protocolPath
    ) {
      return null
    }
    return url.toString()
  } catch {
    return null
  }
}

function discountCodeSourceHost(sourceUrl: string): string {
  return new URL(sourceUrl).hostname.replace(/^www\./, '')
}

function discountCodeEntries(block: Extract<DiscoverChatBlock, { type: 'code' }>) {
  return block.codes ?? []
}

export function DiscoverChatBlockView({
  threadId,
  block,
  researchQuery,
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
  autoStartCheckout = false,
  activeCheckout,
  checkoutBusy,
  checkoutError,
  onCheckoutAssistant,
  onRefreshCheckout,
  onReleaseCheckout,
  onCheckoutHere,
  newsletter,
  newsletterPending,
  onNewsletterSignup,
  onShelfAddProduct,
  onDragProduct,
  onVisibleProductContextChange,
  immutable,
  useLiveCart,
  cartAdditionPending = false,
  agentActionsDisabled,
  compactText = false,
  messageProducts = [],
  onOpenMessageProduct,
}: Readonly<{
  threadId: string
  block: DiscoverChatBlock
  researchQuery?: string | null
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfProductSet: ReadonlySet<ProductId>
  onOpen: (product: Product, products?: readonly Product[], researchQuery?: string | null) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (
    kind: 'reviews' | 'code' | 'similar',
    product: Product,
    query?: string,
    qualificationId?: string,
  ) => void
  onJustPick: (products: readonly Product[]) => void
  onCompareHere: (products: readonly Product[]) => void
  onOpenFullCompare: (products: readonly Product[]) => void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onReviewCartHere: (lines?: readonly CartItem[], products?: readonly Product[]) => void
  onRestoreCartLine: (product: Product, merchant: string, price?: number, offerKey?: string) => void
  onCartQty: (
    id: ProductId,
    merchant: string,
    qty: number,
    nextCart: readonly CartItem[],
    identity?: string,
    quantityDelta?: number,
    sourceItem?: CartItem,
  ) => void
  onCartRemove: (
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
    identity?: string,
    sourceItem?: CartItem,
  ) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  autoStartCheckout?: boolean
  activeCheckout: ActiveCheckoutSession | null
  checkoutBusy: boolean
  checkoutError: string | null
  onCheckoutAssistant: CheckoutAssistantHandler
  onRefreshCheckout: () => Promise<void> | void
  onReleaseCheckout?: CheckoutReleaseHandler
  onCheckoutHere: () => void
  newsletter: boolean
  newsletterPending: boolean
  onNewsletterSignup: () => void
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
  onVisibleProductContextChange?: VisibleProductContextChange
  immutable: boolean
  useLiveCart: boolean
  cartAdditionPending?: boolean
  agentActionsDisabled: boolean
  compactText?: boolean
  messageProducts?: readonly Product[]
  onOpenMessageProduct?: (product: Product) => void
}>) {
  const openProduct = productOpenWithResearchQuery(onOpen, researchQuery)

  if (block.type === 'text') {
    return (
      <AgentMarkdown
        text={block.text}
        compact={compactText}
        products={messageProducts}
        onOpenProduct={onOpenMessageProduct ?? ((product) => openProduct(product))}
      />
    )
  }
  if (block.type === 'system') {
    return (
      <div className="mt-ct-system">
        <SparkMark size={11} color="var(--faint)" />
        {block.text}
      </div>
    )
  }
  if (block.type === 'newsletter') {
    return (
      <ComingSoonNewsletter
        newsletter={newsletter}
        newsletterPending={newsletterPending}
        onNewsletterSignup={onNewsletterSignup}
      />
    )
  }
  if (block.type === 'products') {
    return (
      <DiscoverProductBatch
        products={block.products}
        query={block.query}
        qualificationId={block.qualificationId}
        deliveryLocations={deliveryLocations}
        preferences={preferences}
        savedSet={savedSet}
        savePendingSet={savePendingSet}
        pinnedSet={pinnedSet}
        watchedSet={watchedSet}
        shelfProductSet={shelfProductSet}
        onOpen={openProduct}
        onToggleSave={onToggleSave}
        onAddCart={onAddCart}
        onPin={onPin}
        onWatch={onWatch}
        onDig={onDig}
        onJustPick={onJustPick}
        onCompareHere={onCompareHere}
        onShelfAddProduct={onShelfAddProduct}
        onDragProduct={onDragProduct}
        sourceMessageId={block.sourceMessageId}
        onVisibleProductContextChange={onVisibleProductContextChange}
        agentActionsDisabled={agentActionsDisabled}
      />
    )
  }
  if (block.type === 'reviews') {
    return (
      <ProductReviewsPanel product={block.product} mode="chat" initialResponse={block.snapshot} />
    )
  }
  if (block.type === 'code') {
    const offer = bestOffer(block.product, deliveryLocations)
    const codes = discountCodeEntries(block)
    const status = block.status ?? (codes.length > 0 ? 'found' : 'empty')
    const merchant = merchantAdjacentDisplayLabel(block.merchant ?? offer?.merchant)
    const statusLabel =
      status === 'error'
        ? 'Search unavailable'
        : codes.length > 0
          ? `${codes.length} valid code${codes.length === 1 ? '' : 's'}${block.cached ? ' · cached' : ''}`
          : 'No accepted code'
    return (
      <div className="mt-ct-block mt-ct-code">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Discount check · {merchant}</div>
          <span className="mt-ct-code-save mt-mono">{statusLabel}</span>
        </div>
        {codes.length > 0 ? (
          <div className="mt-ct-code-list">
            {codes.map((code) => {
              const sourceUrl = safeDiscountCodeSourceUrl(code.sourceUrl)
              const sourceHost = sourceUrl ? discountCodeSourceHost(sourceUrl) : null
              const detail =
                code.description ||
                code.title ||
                code.validationMessage ||
                'Accepted by the merchant cart.'
              const validationMeta =
                code.validationMessage && code.validationMessage !== detail
                  ? code.validationMessage
                  : null
              return (
                <div className="mt-ct-code-row" key={code.code}>
                  <span className="mt-code">
                    <span className="mt-code-val mt-mono">{code.code}</span>
                    <span className="mt-code-act mt-mono">valid</span>
                    <button
                      className="mt-code-copy"
                      type="button"
                      title={`Copy ${code.code}`}
                      aria-label={`Copy ${code.code}`}
                      onClick={() => copyTextToClipboard(code.code)}
                    >
                      <CopyIcon size={12} />
                    </button>
                  </span>
                  <div className="mt-ct-code-detail">
                    <div className="mt-ct-code-label">{detail}</div>
                    <div className="mt-ct-code-meta mt-mono">
                      {code.restrictions ? <span>{code.restrictions}</span> : null}
                      {sourceHost && sourceUrl ? (
                        <a href={sourceUrl} target="_blank" rel="noreferrer">
                          {sourceHost}
                        </a>
                      ) : null}
                      {validationMeta ? <span>{validationMeta}</span> : null}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          <div className={`mt-ct-code-empty ${status === 'error' ? 'error' : ''}`}>
            {block.message ?? `No accepted discount code found for ${merchant}.`}
          </div>
        )}
      </div>
    )
  }
  if (block.type === 'similar') {
    return (
      <div className="mt-ct-block">
        {block.similarityAnchor ? null : (
          <div className="mt-ct-block-head">
            <div className="mt-mono mt-ct-block-key">
              {block.product ? `Similar to ${block.product.name}` : 'Similar products'}
            </div>
          </div>
        )}
        <DiscoverProductBatch
          products={block.products}
          query={researchQuery ?? undefined}
          qualificationId={block.qualificationId}
          deliveryLocations={deliveryLocations}
          preferences={preferences}
          savedSet={savedSet}
          savePendingSet={savePendingSet}
          pinnedSet={pinnedSet}
          watchedSet={watchedSet}
          shelfProductSet={shelfProductSet}
          onOpen={openProduct}
          onToggleSave={onToggleSave}
          onAddCart={onAddCart}
          onPin={onPin}
          onWatch={onWatch}
          onDig={onDig}
          onJustPick={onJustPick}
          onCompareHere={onCompareHere}
          onShelfAddProduct={onShelfAddProduct}
          onDragProduct={onDragProduct}
          sourceMessageId={block.sourceMessageId}
          onVisibleProductContextChange={onVisibleProductContextChange}
          agentActionsDisabled={agentActionsDisabled}
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
            {block.product.rankingUnavailable
              ? 'Saved search order'
              : `${Math.max(76, block.product.match)}% confident`}
          </span>
        </div>
        <button
          className="mt-ct-decision-prod"
          type="button"
          onClick={() => openProduct(block.product)}
        >
          <span className="mt-ct-decision-media">
            <ProductArtwork product={block.product} label={block.product.category.toLowerCase()} />
          </span>
          <span className="mt-ct-decision-info">
            <span className="mt-mono mt-ct-decision-brand">
              {merchantAdjacentDisplayLabel(block.product.brand)}
            </span>
            <span className="mt-ct-decision-name">{block.product.name}</span>
            <span className="mt-ct-decision-price">
              {money(
                productPriceFrom(block.product, deliveryLocations),
                block.product.priceCurrency,
              )}
            </span>
          </span>
        </button>
        <p className="mt-ct-decision-why">{productCuratedTake(block.product, preferences)}</p>
        {block.runnerUp ? (
          <div className="mt-ct-decision-beat">
            <span className="mt-mono">vs.</span>{' '}
            {block.product.rankingUnavailable
              ? `Preferred over ${block.runnerUp.name} in the saved search order.`
              : `Beat ${block.runnerUp.name} on match score and fit.`}
          </div>
        ) : null}
        <div className="mt-ct-decision-actions">
          <button
            className="mt-ct-addbtn solid"
            type="button"
            disabled={agentActionsDisabled}
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
          <div className="mt-mono mt-ct-watchalert-key">Price watch</div>
          <div className="mt-ct-watchalert-text">
            The <b>{block.product.name}</b> dropped to{' '}
            {money(block.price, block.product.priceCurrency)} at{' '}
            {merchantAdjacentDisplayLabel(block.merchant)}.
          </div>
        </div>
        <button
          className="mt-ct-addbtn solid"
          type="button"
          disabled={agentActionsDisabled}
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
          <button
            className="mt-ct-friend-prod"
            type="button"
            onClick={() => openProduct(block.product)}
          >
            <span className="mt-ct-friend-thumb">
              <ProductArtwork
                product={block.product}
                label={block.product.category.toLowerCase()}
              />
            </span>
            <span className="mt-ct-friend-name">{block.product.name}</span>
          </button>
          {block.vote === 'up' ? (
            <button
              className="mt-ct-addbtn"
              type="button"
              disabled={agentActionsDisabled}
              onClick={() => onAddCart(block.product)}
            >
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
            {money(addedPrice, block.product.priceCurrency)} ·{' '}
            {merchantAdjacentDisplayLabel(block.merchant)} · {addedCount} in cart
          </span>
        </div>
        <div className="mt-ct-added-actions">
          <button
            className="mt-ct-added-go"
            type="button"
            onClick={() => {
              const liveLine = cartLineForAddedBlock(cart, block)
              if (!liveLine && addedPrice != null) {
                onRestoreCartLine(block.product, block.merchant, addedPrice, block.offerKey)
              }
              onReviewCartHere(
                [
                  liveLine ?? {
                    id: block.product.id,
                    merchant: block.merchant,
                    qty: 1,
                    offerKey: block.offerKey,
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
                onClick={() => openProduct(product)}
              >
                <span className="mt-ct-mini2-media">
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                </span>
                <span className="mt-ct-mini2-name">{product.name}</span>
                <span className="mt-ct-mini2-price">
                  {money(productPriceFrom(product, deliveryLocations), product.priceCurrency)}
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
  if (block.type === 'mission') {
    const covered = block.requirements.filter(
      (requirement) => requirement.state === 'COVERED' || requirement.state === 'OPTIONAL',
    ).length
    return (
      <div className="mt-ct-block mt-ct-mission">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Shopping mission</div>
          <span className="mt-ct-code-save mt-mono">
            {covered}/{block.requirements.length} covered · {block.status.replaceAll('_', ' ')}
          </span>
        </div>
        <p className="mt-ct-mission-goal">{block.goal}</p>
        {block.requirements.length > 0 ? (
          <ul className="mt-ct-mission-list">
            {block.requirements.map((requirement) => (
              <li
                className={`mt-ct-mission-item ${requirement.state.toLowerCase()}`}
                key={requirement.id}
              >
                <span className="mt-ct-mission-check" aria-hidden="true">
                  {requirement.state === 'COVERED'
                    ? '✓'
                    : requirement.state === 'PARTIAL'
                      ? '◐'
                      : '○'}
                </span>
                <span>{requirement.label}</span>
                <span className="mt-mono mt-ct-mission-count">
                  {requirement.coveredQuantity}/{requirement.requiredQuantity}
                </span>
              </li>
            ))}
          </ul>
        ) : null}
        {block.assumptions.length > 0 ? (
          <div className="mt-ct-mission-assumptions">
            <span className="mt-mono">Assumptions</span>
            <span>{block.assumptions.join(' · ')}</span>
          </div>
        ) : null}
      </div>
    )
  }
  if (block.type === 'cart') {
    return (
      <InlineCartBlock
        cart={cartItemsForChatBlock(cart, block.lines, immutable, useLiveCart)}
        products={productsWithFallback(block.products, cartProducts)}
        onQty={onCartQty}
        onRemove={onCartRemove}
        onAddCart={onAddCart}
        onOpenCart={onOpenCart}
        onCheckoutHere={onCheckoutHere}
        loading={useLiveCart && cartAdditionPending}
        agentActionsDisabled={agentActionsDisabled}
      />
    )
  }
  if (block.type === 'checkout') {
    return (
      <InlineCheckoutBlock
        threadId={threadId}
        cart={block.lines ?? cart}
        products={productsWithFallback(block.products, cartProducts)}
        actionCart={block.lines ? cart : undefined}
        actionProducts={block.lines ? cartProducts : undefined}
        onCheckout={onCheckout}
        autoStartCheckout={autoStartCheckout}
        activeCheckout={activeCheckout}
        checkoutBusy={checkoutBusy}
        checkoutError={checkoutError}
        onCheckoutAssistant={onCheckoutAssistant}
        onRefreshCheckout={onRefreshCheckout}
        onReleaseCheckout={onReleaseCheckout}
        onOpenCart={onOpenCart}
        onOpenOrders={onOpenOrders}
      />
    )
  }
  return (
    <InlineMiniCompareBlock
      block={block}
      deliveryLocations={deliveryLocations}
      onOpen={openProduct}
      onAddCart={onAddCart}
      onOpenFullCompare={onOpenFullCompare}
      agentActionsDisabled={agentActionsDisabled}
    />
  )
}
