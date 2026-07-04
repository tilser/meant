import { SparkMark } from '../shared/ui'

export function ProductSearchLoading({
  label = 'Searching products across stores',
}: Readonly<{
  label?: string
}>) {
  return (
    <div
      className="mt-search-state mt-search-state-loading"
      role="status"
      aria-live="polite"
      aria-label={label}
    >
      <div className="mt-search-loader" aria-hidden="true">
        <span className="mt-search-loader-spark">
          <SparkMark size={14} />
        </span>
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
      </div>
      <span className="mt-search-loading-text">
        {label}
        <span className="mt-search-loading-dots" aria-hidden="true">
          <span>.</span>
          <span>.</span>
          <span>.</span>
        </span>
      </span>
    </div>
  )
}
