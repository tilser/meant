import { CloseIcon, MeantHeartMark, ProductArtwork, SparkMark } from '../../shared/ui'
import type { CartItem, Product, ProductId } from '../../types'
import { cartGroups, cartLines, computeSmartAlerts, money } from '../../utils'
import { merchantDisplayOrigin } from '../../cart/merchantOrigin'
import { cartItemIdentity } from '../utils'

export function InlineCartBlock({
  cart,
  products,
  onQty,
  onRemove,
  onAddCart,
  onOpenCart,
  onCheckoutHere,
  loading = false,
  agentActionsDisabled = false,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  onQty: (
    id: ProductId,
    merchant: string,
    qty: number,
    nextCart: readonly CartItem[],
    identity?: string,
    quantityDelta?: number,
    sourceItem?: CartItem,
  ) => void
  onRemove: (
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
    identity?: string,
    sourceItem?: CartItem,
  ) => void
  onAddCart: (product: Product) => void
  onOpenCart: () => void
  onCheckoutHere: () => void
  loading?: boolean
  agentActionsDisabled?: boolean
}>) {
  const lines = cartLines(cart, products)
  const alerts = computeSmartAlerts(lines, products)
  const groups = cartGroups(lines)
  const itemCount = lines.reduce((sum, line) => sum + line.qty, 0)
  const showInitialLoading = loading && lines.length === 0
  const total = groups.reduce((sum, group) => sum + group.total, 0)
  const currencies = new Set(
    groups.map((group) => group.currency).filter((value): value is string => Boolean(value)),
  )
  const totalCurrency = currencies.size === 1 ? currencies.values().next().value : null
  const cartMeta = showInitialLoading
    ? 'Adding your find…'
    : `${itemCount} item${itemCount === 1 ? '' : 's'} · ${groups.length} merchant${groups.length === 1 ? '' : 's'}`
  const productById = new Map(products.map((product) => [product.id, product]))
  const cartAfterQty = (target: CartItem, qty: number) =>
    qty <= 0
      ? cart.filter((item) => cartItemIdentity(item) !== cartItemIdentity(target))
      : cart.map((item) =>
          cartItemIdentity(item) === cartItemIdentity(target) ? { ...item, qty } : item,
        )
  const cartAfterRemove = (target: CartItem) =>
    cart.filter((item) => cartItemIdentity(item) !== cartItemIdentity(target))

  return (
    <div className="mt-ct-block mt-ct-cart">
      <div className="mt-ct-cart-head">
        <div className="mt-ct-cart-brand">
          <span className="mt-ct-cart-brand-mark" aria-hidden>
            <MeantHeartMark size={20} />
          </span>
          <div className="mt-ct-cart-brand-copy">
            <div className="mt-mono mt-ct-block-key">Cart in chat</div>
            <strong>
              Your <em>Meant</em> finds
            </strong>
            <span>{cartMeta}</span>
          </div>
        </div>
        <div className="mt-ct-cart-head-actions">
          {!showInitialLoading && lines.length > 0 ? (
            <div className="mt-ct-cart-total">
              <span>Cart total</span>
              <strong>{totalCurrency ? money(total, totalCurrency) : 'Per merchant'}</strong>
            </div>
          ) : null}
          <button className="mt-ct-cart-openfull" type="button" onClick={onOpenCart}>
            <span>Full cart</span>
            <svg width="13" height="13" viewBox="0 0 16 16" fill="none" aria-hidden>
              <path
                d="M3.5 8h9m-3.4-3.4L12.5 8l-3.4 3.4"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>
      {alerts.length > 0 ? (
        <div className="mt-ct-cart-signals">
          {alerts.slice(0, 2).map((alert) => (
            <div className={`mt-cart-sig mt-cart-sig-${alert.kind}`} key={alert.id}>
              <b>{alert.title}</b>
              <span>{alert.body}</span>
              {alert.fix ? (
                <button
                  className="mt-ct-cart-fix"
                  type="button"
                  disabled={agentActionsDisabled}
                  onClick={() => {
                    const product = productById.get(alert.fix?.id ?? '')
                    if (product) {
                      onAddCart(product)
                    }
                  }}
                >
                  {alert.fix.label}
                </button>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
      {showInitialLoading ? (
        <div className="mt-ct-cart-loading" role="status" aria-live="polite">
          <span className="mt-ct-cart-loading-spinner" aria-hidden="true" />
          <span>
            <strong>Adding item to cart…</strong>
            <span>Confirming availability with the merchant.</span>
          </span>
        </div>
      ) : lines.length > 0 ? (
        <div className="mt-ct-cart-list">
          {lines.map((line) => (
            <div
              className={`mt-ct-cart-row${line.syncing ? ' is-syncing' : ''}${line.syncError ? ' has-error' : ''}`}
              key={cartItemIdentity(line)}
            >
              <button
                className="mt-ct-cart-media"
                type="button"
                aria-label={`Open ${line.product.name} in full cart`}
                onClick={() => onOpenCart()}
              >
                <ProductArtwork
                  product={line.product}
                  label={line.product.category.toLowerCase()}
                />
              </button>
              <div className="mt-ct-cart-info">
                <div className="mt-ct-cart-name">{line.product.name}</div>
                <div className="mt-mono mt-ct-cart-meta">
                  {merchantDisplayOrigin(line.merchantOrigin)} · {line.delivery}
                </div>
                {line.syncing || line.syncError ? (
                  <div
                    className={`mt-mono mt-ct-cart-sync${line.syncError ? ' is-error' : ''}`}
                    role="status"
                  >
                    {line.syncError ?? 'Saving…'}
                  </div>
                ) : null}
              </div>
              <div className="mt-ct-cart-actions">
                <div className="mt-qty" aria-label={`Quantity for ${line.product.name}`}>
                  <button
                    type="button"
                    aria-label="Decrease quantity"
                    onClick={() =>
                      onQty(
                        line.id,
                        line.merchant,
                        line.qty - 1,
                        cartAfterQty(line, line.qty - 1),
                        cartItemIdentity(line),
                        -1,
                        line,
                      )
                    }
                  >
                    -
                  </button>
                  <span>{line.qty}</span>
                  <button
                    type="button"
                    aria-label="Increase quantity"
                    onClick={() =>
                      onQty(
                        line.id,
                        line.merchant,
                        line.qty + 1,
                        cartAfterQty(line, line.qty + 1),
                        cartItemIdentity(line),
                        1,
                        line,
                      )
                    }
                  >
                    +
                  </button>
                </div>
                <span className="mt-ct-cart-price">
                  {money(line.price * line.qty, line.priceCurrency)}
                </span>
                <button
                  className="mt-ct-cart-remove"
                  type="button"
                  aria-label={`Remove ${line.product.name}`}
                  onClick={() =>
                    onRemove(
                      line.id,
                      line.merchant,
                      cartAfterRemove(line),
                      cartItemIdentity(line),
                      line,
                    )
                  }
                >
                  <CloseIcon size={12} />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-ct-cart-empty">
          <span className="mt-ct-cart-empty-mark" aria-hidden>
            <MeantHeartMark size={19} />
          </span>
          <span>
            <strong>Nothing Meant for your cart yet.</strong>
            <small>When a find clicks, it’ll wait for you here.</small>
          </span>
        </div>
      )}
      {!showInitialLoading && lines.length > 0 ? (
        <div className="mt-ct-cart-foot">
          <div className="mt-ct-cart-ready">
            <span className="mt-ct-cart-ready-mark" aria-hidden>
              <SparkMark size={14} color="#4d99e8" />
            </span>
            <span>
              <strong>Ready when you are.</strong>
              <small>Checkout continues right here in chat.</small>
            </span>
          </div>
          <button className="mt-ct-cart-checkout" type="button" onClick={onCheckoutHere}>
            <span>Checkout in chat</span>
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden>
              <path
                d="M3.5 8h9m-3.4-3.4L12.5 8l-3.4 3.4"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </div>
      ) : null}
    </div>
  )
}
