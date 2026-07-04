export function MerchantIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M3 3h12l-.8 4a2 2 0 0 1-2 1.6H5.8a2 2 0 0 1-2-1.6L3 3zM4 8.6V15h10V8.6"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function SunIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.6" />
      <path
        d="M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function MoonIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M20 14.5A8 8 0 0 1 9.5 4a7 7 0 1 0 10.5 10.5z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function HeartIcon({ filled }: Readonly<{ filled: boolean }>) {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M9 15.5S2.5 11.5 2.5 6.8A3.3 3.3 0 0 1 9 5.2a3.3 3.3 0 0 1 6.5 1.6C15.5 11.5 9 15.5 9 15.5z"
        fill={filled ? 'var(--accent)' : 'none'}
        stroke={filled ? 'var(--accent)' : 'currentColor'}
        strokeWidth="1.4"
      />
    </svg>
  )
}

export function ChevronIcon({
  direction,
  size = 18,
}: Readonly<{ direction: 'left' | 'right'; size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <path
        d={direction === 'left' ? 'M11 3 6 9l5 6' : 'M7 3l5 6-5 6'}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function HistoryIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <path
        d="M5.4 5.2H2.8V2.6M3 8.8a6 6 0 1 0 1.9-4.4L2.8 6.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M9 5.7V9l2.4 1.4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function PlusIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <path
        d="M9 3.8v10.4M3.8 9h10.4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function ShareIcon({ size = 14 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden>
      <circle cx="4.5" cy="9" r="1.9" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="13.5" cy="4.5" r="1.9" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="13.5" cy="13.5" r="1.9" stroke="currentColor" strokeWidth="1.4" />
      <path d="M6.2 8 11.8 5.3M6.2 10l5.6 2.7" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  )
}

export function CopyIcon({ size = 14 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden>
      <rect
        x="6.2"
        y="5.1"
        width="8"
        height="9.6"
        rx="1.5"
        stroke="currentColor"
        strokeWidth="1.4"
      />
      <path
        d="M4 11.9H3.8A1.8 1.8 0 0 1 2 10.1V4a1.8 1.8 0 0 1 1.8-1.8h5A1.8 1.8 0 0 1 10.6 4v.2"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function SearchIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <circle cx="8" cy="8" r="4.6" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <path
        d="M11.5 11.5 15 15"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function BookmarkIcon({
  filled = false,
  size = 14,
}: Readonly<{
  filled?: boolean
  size?: number
}>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill={filled ? 'currentColor' : 'none'}
      aria-hidden
    >
      <path
        d="M4 2h8v12l-4-2.8L4 14V2z"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function CollapseIcon({ collapsed }: Readonly<{ collapsed: boolean }>) {
  return (
    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d={collapsed ? 'M4 6l4 4 4-4' : 'M4 10l4-4 4 4'}
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function OpenIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d="M5.5 3.5h7v7M12 4 4 12"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function MatchRing({
  value,
  size = 44,
  stroke = 3,
}: Readonly<{
  value: number
  size?: number
  stroke?: number
}>) {
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference * (1 - value / 100)
  return (
    <div className="mt-ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} aria-hidden>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--line)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={value >= 85 ? 'var(--accent)' : 'var(--muted)'}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="mt-mono mt-ring-num" style={{ fontSize: size * 0.26 }}>
        {value}
      </span>
    </div>
  )
}

export function PrefChip({
  label,
  variant = 'muted',
  small = false,
}: Readonly<{
  label: string
  variant?: 'lit' | 'muted' | 'missed'
  small?: boolean
}>) {
  return (
    <span className={`mt-chip mt-chip-${variant} mt-chip-in ${small ? 'mt-chip-sm' : ''}`}>
      {variant === 'lit' && <span className="mt-chip-dot" />}
      {variant === 'missed' && <span className="mt-chip-x">x</span>}
      {label}
    </span>
  )
}
