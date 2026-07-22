import { useEffect, useMemo, useRef, useState } from 'react'

import type { MerchantProfile } from '../../../lib/apiClient'
import { MerchantIcon } from '../shared/icons'

export function MerchantScope({
  merchants,
  selectedMerchantId,
  loading,
  error,
  onMerchant,
}: Readonly<{
  merchants: readonly MerchantProfile[]
  selectedMerchantId: string | null
  loading: boolean
  error: string | null
  onMerchant: (merchantId: string | null) => void
}>) {
  const [open, setOpen] = useState(false)
  const [merchantSearch, setMerchantSearch] = useState('')
  const ref = useRef<HTMLDivElement | null>(null)
  const searchRef = useRef<HTMLInputElement | null>(null)
  const selectedMerchant = merchants.find((merchant) => merchant.id === selectedMerchantId) ?? null
  const normalizedSearch = merchantSearch.trim().toLocaleLowerCase()
  const filteredMerchants = useMemo(
    () =>
      merchants.filter((merchant) => {
        if (!normalizedSearch) return true
        return [
          merchant.name,
          merchant.domain,
          merchant.description,
          merchant.advertisedMcpEndpoint,
          merchant.profileMcpEndpoint,
        ]
          .filter((value): value is string => Boolean(value))
          .join(' ')
          .toLocaleLowerCase()
          .includes(normalizedSearch)
      }),
    [merchants, normalizedSearch],
  )

  useEffect(() => {
    if (!open) return
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      setMerchantSearch('')
      return
    }
    const timeout = window.setTimeout(() => searchRef.current?.focus(), 0)
    return () => window.clearTimeout(timeout)
  }, [open])

  if (error) {
    return null
  }

  if (merchants.length === 0) {
    return (
      <div className="mt-scope">
        <button
          className="mt-scope-btn"
          type="button"
          disabled
          title={loading ? 'Loading merchants' : 'No active merchants available'}
        >
          <MerchantIcon />
          <span>{loading ? 'Loading merchants' : 'All merchants'}</span>
        </button>
      </div>
    )
  }

  return (
    <div className="mt-scope">
      <div className="mt-scope-combo" ref={ref}>
        <button
          className={`mt-scope-btn ${selectedMerchant ? 'on' : ''}`}
          type="button"
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
        >
          {selectedMerchant ? <span className="mt-scope-dot" aria-hidden /> : <MerchantIcon />}
          <span>{selectedMerchant?.name ?? 'All merchants'}</span>
          <span className={`mt-caret ${open ? 'up' : ''}`} aria-hidden>
            v
          </span>
        </button>
        {open ? (
          <div className="mt-scope-menu">
            <label className="mt-scope-search">
              <span className="mt-scope-search-icon" aria-hidden>
                <svg width="14" height="14" viewBox="0 0 18 18" fill="none">
                  <circle cx="8" cy="8" r="4.6" stroke="currentColor" strokeWidth="1.4" />
                  <path
                    d="M11.4 11.4 15 15"
                    stroke="currentColor"
                    strokeWidth="1.4"
                    strokeLinecap="round"
                  />
                </svg>
              </span>
              <input
                ref={searchRef}
                className="mt-scope-search-input"
                value={merchantSearch}
                onChange={(event) => setMerchantSearch(event.target.value)}
                placeholder="Search merchants"
                aria-label="Search merchants"
              />
            </label>
            <div className="mt-scope-list" role="listbox" aria-label="Merchant search scope">
              <button
                className={`mt-scope-opt ${selectedMerchant ? '' : 'on'}`}
                type="button"
                role="option"
                aria-selected={!selectedMerchant}
                onClick={() => {
                  onMerchant(null)
                  setOpen(false)
                }}
              >
                <span className="mt-scope-opt-name">All merchants</span>
              </button>
              <div className="mt-scope-sep" />
              {filteredMerchants.map((merchant) => (
                <button
                  key={merchant.id}
                  className={`mt-scope-opt ${selectedMerchant?.id === merchant.id ? 'on' : ''}`}
                  type="button"
                  role="option"
                  aria-selected={selectedMerchant?.id === merchant.id}
                  onClick={() => {
                    onMerchant(merchant.id)
                    setOpen(false)
                  }}
                >
                  <span className="mt-scope-opt-main">
                    <span className="mt-scope-opt-name">{merchant.name}</span>
                    <span className="mt-mono mt-scope-opt-domain">{merchant.domain}</span>
                  </span>
                </button>
              ))}
              {filteredMerchants.length === 0 ? (
                <div className="mt-scope-empty mt-mono">No merchants found</div>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
