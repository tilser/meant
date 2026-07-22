import { type ReactNode, useId } from 'react'

import type { Product } from '../types'

interface ViewHeadProps {
  eyebrow: string
  title: string
  sub?: string
  right?: ReactNode
}

export function SparkMark({
  size = 16,
  color = 'var(--accent)',
}: Readonly<{
  size?: number
  color?: string
}>) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden>
      <path
        d="M10 2.5l1.7 4.8 4.8 1.7-4.8 1.7L10 17.5l-1.7-4.8L3.5 11l4.8-1.7L10 2.5z"
        fill={color}
      />
    </svg>
  )
}

export function MeantHeartMark({ size = 16 }: Readonly<{ size?: number }>) {
  const gradientId = useId()

  return (
    <svg
      className="mt-meant-heart-mark"
      width={size}
      height={size}
      viewBox="0 0 48 44"
      fill="none"
      aria-hidden
    >
      <defs>
        <linearGradient id={gradientId} x1="5" y1="5" x2="43" y2="39">
          <stop offset="0" stopColor="#16d3df" />
          <stop offset="0.48" stopColor="#4d99e8" />
          <stop offset="1" stopColor="#9847d6" />
        </linearGradient>
      </defs>
      <path
        d="M24 40.2C21.3 38 5 26.4 5 14.2 5 7.5 9.5 3.5 15.1 3.5c3.8 0 7.1 2 8.9 5.2 1.8-3.2 5.1-5.2 8.9-5.2 5.6 0 10.1 4 10.1 10.7 0 12.2-16.3 23.8-19 26Z"
        fill={`url(#${gradientId})`}
      />
      <path
        d="m15.2 20.7 6.2 5.7 12.1-12"
        stroke="#0b2b5c"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="5.2"
      />
    </svg>
  )
}

export function CartIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M2 2.5h2.1l1.4 8.2h7.2l1.4-6.1H5.1"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="7" cy="14.6" r="1.15" fill="currentColor" />
      <circle cx="12.6" cy="14.6" r="1.15" fill="currentColor" />
    </svg>
  )
}

export function CloseIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" aria-hidden>
      <path
        d="M3 3l10 10M13 3 3 13"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function Placeholder({
  label,
  tone,
  radius = 0,
}: Readonly<{
  label: string
  tone: string
  radius?: number
}>) {
  return (
    <div className="mt-ph" style={{ background: tone, borderRadius: radius }}>
      <div className="mt-ph-stripes" />
      <span className="mt-mono mt-ph-label">{label}</span>
    </div>
  )
}

export function ProductArtwork({
  product,
  label,
  imageUrl,
}: Readonly<{
  product: Product
  label: string
  imageUrl?: string | null
}>) {
  const artworkUrl =
    imageUrl?.trim() ||
    product.imageUrl?.trim() ||
    product.media
      ?.filter((item) => item.type?.toLowerCase() === 'image')
      ?.map((item) => item.url?.trim() ?? '')
      ?.find(Boolean)
  if (artworkUrl) {
    return <img className="mt-product-img" src={artworkUrl} alt="" loading="lazy" />
  }
  return <Placeholder label={label} tone={product.tone} />
}

export function ViewHead({ eyebrow, title, sub, right }: Readonly<ViewHeadProps>) {
  return (
    <div className="mt-view-head">
      <div>
        <div className="mt-mono mt-view-eyebrow">{eyebrow}</div>
        <h1 className="mt-view-title">{title}</h1>
        {sub ? <p className="mt-view-sub">{sub}</p> : null}
      </div>
      {right}
    </div>
  )
}

export function EmptyState({
  title,
  sub,
  mark,
}: Readonly<{
  title: string
  sub: string
  mark: ReactNode
}>) {
  return (
    <div className="mt-empty">
      <div className="mt-empty-mark">{mark}</div>
      <h3 className="mt-empty-title">{title}</h3>
      <p className="mt-empty-sub">{sub}</p>
    </div>
  )
}
