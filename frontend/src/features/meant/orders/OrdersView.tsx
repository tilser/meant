import { useEffect, useState } from 'react'

import { PrefChip } from '../shared/icons'
import { CartIcon, EmptyState, ProductArtwork, ViewHead } from '../shared/ui'
import { merchantAdjacentDisplayLabel, merchantDisplayOrigin } from '../cart/merchantOrigin'
import type { Order, Preference, Product } from '../types'
import { formatOrderDate, money, prefLabel } from '../utils'
import { orderLineTotal, orderLineUnitPrice } from './orderMapping'

export function OrdersView({
  orders,
  products,
  loading,
  error,
  flashId,
  preferences,
  onOpen,
  onReorder,
}: Readonly<{
  orders: readonly Order[]
  products: readonly Product[]
  loading: boolean
  error: string | null
  flashId: string | null
  preferences: readonly Preference[]
  onOpen: (product: Product) => void
  onReorder: (order: Order) => void
}>) {
  const [page, setPage] = useState(0)
  const pageSize = 3
  const pageCount = Math.ceil(orders.length / pageSize)
  const safePage = Math.min(page, Math.max(0, pageCount - 1))
  const start = safePage * pageSize
  const visible = orders.slice(start, start + pageSize)
  const totalSaved = orders.reduce((sum, order) => sum + order.saved, 0)

  useEffect(() => {
    if (flashId) {
      setPage(0)
    }
  }, [flashId])

  if (orders.length === 0) {
    return (
      <main className="mt-feed mt-view">
        <ViewHead eyebrow="Your purchases" title="Order history" />
        <EmptyState
          title={loading ? 'Loading orders' : error ? 'Could not load orders' : 'No orders yet'}
          sub={error ?? 'When you check out, your orders land here.'}
          mark={<CartIcon />}
        />
      </main>
    )
  }

  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Your purchases"
        title="Order history"
        sub={`${orders.length} orders across your stores, each one filtered to what matters to you.`}
        right={
          totalSaved > 0 ? (
            <div className="mt-order-lifetime">
              <span className="mt-mono mt-order-lifetime-k">Saved with Meant</span>
              <span className="mt-order-lifetime-v">{money(totalSaved)}</span>
            </div>
          ) : undefined
        }
      />
      <div className="mt-orders">
        {flashId && safePage === 0 ? (
          <div className="mt-order-flash">Order {flashId} placed. Meant is preparing dispatch.</div>
        ) : null}
        {visible.map((order) => (
          <OrderCard
            key={order.id}
            order={order}
            products={products}
            preferences={preferences}
            flash={order.id === flashId}
            onOpen={onOpen}
            onReorder={onReorder}
          />
        ))}
      </div>
      {pageCount > 1 ? (
        <nav className="mt-pager" aria-label="Order history pages">
          <span className="mt-mono mt-pager-info">
            Showing {start + 1}-{Math.min(start + pageSize, orders.length)} of {orders.length}
          </span>
          <div className="mt-pager-ctrls">
            <button
              className="mt-pager-btn"
              type="button"
              disabled={safePage === 0}
              onClick={() => setPage(safePage - 1)}
            >
              Prev
            </button>
            {Array.from({ length: pageCount }).map((_, index) => (
              <button
                key={index}
                className={`mt-pager-num mt-mono ${index === safePage ? 'on' : ''}`}
                type="button"
                aria-current={index === safePage ? 'page' : undefined}
                onClick={() => setPage(index)}
              >
                {index + 1}
              </button>
            ))}
            <button
              className="mt-pager-btn"
              type="button"
              disabled={safePage === pageCount - 1}
              onClick={() => setPage(safePage + 1)}
            >
              Next
            </button>
          </div>
        </nav>
      ) : null}
    </main>
  )
}

function OrderCard({
  order,
  products,
  preferences,
  flash,
  onOpen,
  onReorder,
}: Readonly<{
  order: Order
  products: readonly Product[]
  preferences: readonly Preference[]
  flash: boolean
  onOpen: (product: Product) => void
  onReorder: (order: Order) => void
}>) {
  const lines = order.items.map((item) => ({
    item,
    product: products.find((product) => product.id === item.id),
  }))
  const total = lines.reduce((sum, { item, product }) => sum + orderLineTotal(item, product), 0)
  const matched = Array.from(new Set(lines.flatMap((line) => line.product?.satisfies ?? [])))

  return (
    <div className={`mt-order ${flash ? 'flash' : ''}`}>
      <div className="mt-order-head">
        <div className="mt-order-head-l">
          <span className="mt-mono mt-order-id">{order.id}</span>
          <span className="mt-order-date">
            Placed {formatOrderDate(order.date)} · {lines.length} items
          </span>
        </div>
        <div className="mt-order-head-r">
          <span
            className={`mt-order-status mt-order-status-${order.status.toLowerCase().replace(/\s+/g, '-')}`}
          >
            <span className="mt-order-status-dot" /> {order.status}
          </span>
          <span className="mt-order-total">{money(total)}</span>
        </div>
      </div>
      <div className="mt-order-track mt-mono">{order.statusNote}</div>
      <div className="mt-order-items">
        {lines.map(({ item, product }) => {
          const unitPrice = orderLineUnitPrice(item, product)
          const lineTotal = orderLineTotal(item, product)
          const productName = product?.name ?? item.productTitle ?? item.productVariantId ?? item.id
          const merchantDisplay = merchantDisplayOrigin(item.merchantOrigin)
          const productBrand = merchantAdjacentDisplayLabel(product?.brand, item.merchantOrigin)
          return (
            <button
              className="mt-order-item"
              key={`${item.id}-${item.merchant}`}
              type="button"
              onClick={() => product && onOpen(product)}
              disabled={!product}
            >
              <div className="mt-order-item-media">
                {product ? (
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                ) : item.imageUrl ? (
                  <img className="mt-order-item-img" src={item.imageUrl} alt={productName} />
                ) : (
                  <div className="mt-order-item-fallback mt-mono">
                    {productName.slice(0, 2).toUpperCase()}
                  </div>
                )}
              </div>
              <div className="mt-order-item-info">
                <div className="mt-mono mt-order-item-brand">{productBrand}</div>
                <div className="mt-order-item-name">{productName}</div>
                <div className="mt-mono mt-order-item-meta">
                  {item.qty} × {money(unitPrice)} · {merchantDisplay}
                </div>
              </div>
              <div className="mt-order-item-price">{money(lineTotal)}</div>
            </button>
          )
        })}
      </div>
      <div className="mt-order-foot">
        <div className="mt-order-prefs">
          <span className="mt-mono mt-order-prefs-label">Matched your preferences</span>
          <div className="mt-chips">
            {matched.slice(0, 4).map((id) => (
              <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" small />
            ))}
          </div>
        </div>
        <div className="mt-order-foot-right">
          {order.saved > 0 ? (
            <div className="mt-order-saved mt-mono">
              Meant saved you {money(order.saved)} {order.savedNote ? `· ${order.savedNote}` : ''}
            </div>
          ) : null}
          <button className="mt-order-reorder" type="button" onClick={() => onReorder(order)}>
            Order again
          </button>
        </div>
      </div>
    </div>
  )
}
