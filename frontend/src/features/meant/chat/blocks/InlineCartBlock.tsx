import { CloseIcon, ProductArtwork } from '../../shared/ui'
import type { CartItem, Product, ProductId } from '../../types'
import { cartGroups, cartLines, computeSmartAlerts, money } from '../../utils'
import { cartItemIdentity } from '../utils'

export function InlineCartBlock({
  cart,
  products,
  onQty,
  onRemove,
  onAddCart,
  onOpenCart,
  onCheckoutHere,
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
}>) {
  const lines = cartLines(cart, products)
  const alerts = computeSmartAlerts(lines, products)
  const groups = cartGroups(lines)
  const itemCount = lines.reduce((sum, line) => sum + line.qty, 0)
  const total = groups.reduce((sum, group) => sum + group.total, 0)
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
      <div className="mt-ct-block-head">
        <div className="mt-mono mt-ct-block-key">Cart in chat</div>
        <span className="mt-ct-code-save mt-mono">
          {itemCount} item{itemCount === 1 ? '' : 's'}
        </span>
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
      {lines.length > 0 ? (
        <div className="mt-ct-cart-list">
          {lines.map((line) => (
            <div className="mt-ct-cart-row" key={cartItemIdentity(line)}>
              <button className="mt-ct-cart-media" type="button" onClick={() => onOpenCart()}>
                <ProductArtwork
                  product={line.product}
                  label={line.product.category.toLowerCase()}
                />
              </button>
              <div className="mt-ct-cart-info">
                <div className="mt-ct-cart-name">{line.product.name}</div>
                <div className="mt-mono mt-ct-cart-meta">
                  {line.merchant} · {line.delivery}
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
                <span className="mt-ct-cart-price">{money(line.price * line.qty)}</span>
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
        <p className="mt-ct-cart-empty">Your cart is empty.</p>
      )}
      <div className="mt-ct-cart-foot">
        <div>
          <span className="mt-mono mt-ct-cart-foot-label">
            {groups.length} merchant{groups.length === 1 ? '' : 's'}
          </span>
          <strong>{money(total)}</strong>
        </div>
        <div className="mt-ct-cart-foot-actions">
          <button className="mt-ct-cart-openfull" type="button" onClick={onOpenCart}>
            Full cart
          </button>
          <button
            className="mt-ct-cart-checkout"
            type="button"
            disabled={lines.length === 0}
            onClick={onCheckoutHere}
          >
            Checkout here
          </button>
        </div>
      </div>
    </div>
  )
}
