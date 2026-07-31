import { type ChangeEvent, useEffect, useRef, useState } from 'react'

import {
  deleteProfilePictureFile,
  removeProfilePicture,
  updateProfile,
  updateProfilePicture,
  uploadProfilePictureFile,
  type MerchantIdentityLinkProfile,
  type MerchantProfile,
} from '../../../lib/apiClient'
import { merchantDisplayOrigin } from '../cart/merchantOrigin'
import { ViewHead } from '../shared/ui'
import type { UserAccount } from '../types'
import { Avatar } from './Avatar'

/**
 * Splits a single full-name field into first name + surname the same way the backend does:
 * first whitespace-separated token is the first name, the remainder is the surname.
 */
function splitName(fullName: string): { firstName: string; surname: string | null } {
  const tokens = fullName.trim().split(/\s+/).filter(Boolean)
  const firstName = tokens[0] ?? ''
  const surname = tokens.length > 1 ? tokens.slice(1).join(' ') : null
  return { firstName, surname }
}

const PROFILE_PICTURE_ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const PROFILE_PICTURE_MAX_BYTES = 5 * 1024 * 1024
const CURRENCY_OPTIONS = [
  ['USD', 'US Dollar'],
  ['EUR', 'Euro'],
  ['GBP', 'British Pound'],
  ['CZK', 'Czech Koruna'],
  ['CAD', 'Canadian Dollar'],
  ['AUD', 'Australian Dollar'],
  ['NZD', 'New Zealand Dollar'],
  ['JPY', 'Japanese Yen'],
  ['CHF', 'Swiss Franc'],
  ['PLN', 'Polish Zloty'],
  ['SEK', 'Swedish Krona'],
  ['NOK', 'Norwegian Krone'],
  ['DKK', 'Danish Krone'],
  ['HUF', 'Hungarian Forint'],
  ['CNY', 'Chinese Yuan'],
  ['HKD', 'Hong Kong Dollar'],
  ['SGD', 'Singapore Dollar'],
  ['INR', 'Indian Rupee'],
  ['KRW', 'South Korean Won'],
] as const

export function AccountView({
  user,
  currency,
  userId,
  providerAvatar,
  merchants,
  merchantIdentityLinks,
  merchantIdentityLinksLoading,
  merchantIdentityLinksError,
  onSave,
  onSignOut,
  onEditPrefs,
  onConnectMerchant,
  onRevokeMerchant,
  onNewsletterChange,
  onCurrencyChange,
  onDone,
}: Readonly<{
  user: UserAccount
  currency: string
  userId?: string
  providerAvatar: string | null
  merchants: readonly MerchantProfile[]
  merchantIdentityLinks: readonly MerchantIdentityLinkProfile[]
  merchantIdentityLinksLoading: boolean
  merchantIdentityLinksError: string | null
  onSave: (user: UserAccount) => void
  onSignOut: () => void
  onEditPrefs: () => void
  onConnectMerchant: (merchant: MerchantProfile) => void
  onRevokeMerchant: (merchantId: string) => void
  onNewsletterChange: (newsletter: boolean) => Promise<void> | void
  onCurrencyChange: (currency: string) => Promise<void> | void
  onDone: () => void
}>) {
  const [name, setName] = useState(user.name)
  const [avatar, setAvatar] = useState<string | null>(user.avatar)
  const [avatarPath, setAvatarPath] = useState<string | null>(user.avatarPath)
  const [newsletter, setNewsletter] = useState(user.newsletter)
  const [newsletterSaving, setNewsletterSaving] = useState(false)
  const [newsletterSaved, setNewsletterSaved] = useState(false)
  const [newsletterError, setNewsletterError] = useState<string | null>(null)
  const [selectedCurrency, setSelectedCurrency] = useState(currency)
  const [currencySaving, setCurrencySaving] = useState(false)
  const [currencySaved, setCurrencySaved] = useState(false)
  const [currencyError, setCurrencyError] = useState<string | null>(null)
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)
  const activeUserIdRef = useRef(userId)
  activeUserIdRef.current = userId
  const savedTimeoutRef = useRef<number | null>(null)
  const newsletterSavedTimeoutRef = useRef<number | null>(null)
  const preview: UserAccount = { name, email: user.email, avatar, avatarPath, newsletter }
  const dirty = name !== user.name || avatarPath !== user.avatarPath || pendingFile !== null
  const hasProfilePicture = Boolean(avatar || avatarPath)
  const hasCustomProfilePicture = pendingFile !== null || avatarPath !== null
  const identityLinksByMerchant = new Map(
    (merchantIdentityLinks ?? []).map((link) => [link.merchantId, link] as const),
  )
  const linkableMerchants = merchants.filter((merchant) => merchant.supportsIdentityLinking)

  useEffect(() => {
    return () => {
      activeUserIdRef.current = undefined
      if (savedTimeoutRef.current !== null) {
        window.clearTimeout(savedTimeoutRef.current)
      }
      if (newsletterSavedTimeoutRef.current !== null) {
        window.clearTimeout(newsletterSavedTimeoutRef.current)
      }
    }
  }, [])

  useEffect(() => {
    setNewsletter(user.newsletter)
  }, [user.newsletter])

  useEffect(() => {
    setSelectedCurrency(currency)
    setCurrencySaved(false)
    setCurrencyError(null)
  }, [currency])

  const showSaved = () => {
    if (savedTimeoutRef.current !== null) {
      window.clearTimeout(savedTimeoutRef.current)
    }
    setSaved(true)
    savedTimeoutRef.current = window.setTimeout(() => {
      setSaved(false)
      savedTimeoutRef.current = null
    }, 1800)
  }

  const showNewsletterSaved = () => {
    if (newsletterSavedTimeoutRef.current !== null) {
      window.clearTimeout(newsletterSavedTimeoutRef.current)
    }
    setNewsletterSaved(true)
    newsletterSavedTimeoutRef.current = window.setTimeout(() => {
      setNewsletterSaved(false)
      newsletterSavedTimeoutRef.current = null
    }, 1800)
  }

  const onFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    setSaved(false)
    setError(null)
    if (!PROFILE_PICTURE_ALLOWED_TYPES.has(file.type)) {
      setError('Use a JPG, PNG, or WebP image.')
      event.target.value = ''
      return
    }
    if (file.size > PROFILE_PICTURE_MAX_BYTES) {
      setError('Profile photo must be 5 MB or smaller.')
      event.target.value = ''
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setAvatar(reader.result)
        setPendingFile(file)
      }
    }
    reader.readAsDataURL(file)
    event.target.value = ''
  }

  const saveAccount = async () => {
    const requestUserId = userId
    if (!requestUserId) {
      setError('Missing authenticated user id')
      return
    }
    const ensureCurrentAccount = () => {
      if (activeUserIdRef.current !== requestUserId) {
        throw new Error('Account changed while saving profile')
      }
    }
    const nextName = name.trim() || user.name
    const { firstName, surname } = splitName(nextName)
    let uploadedPath: string | null = null
    setSaving(true)
    setError(null)
    try {
      let savedName = nextName
      let savedEmail = user.email
      let savedAvatar = avatar
      let savedAvatarPath = avatarPath

      if (nextName !== user.name) {
        const profile = await updateProfile(
          { firstName, surname },
          { expectedUserId: requestUserId },
        )
        ensureCurrentAccount()
        savedName =
          [profile.firstName, profile.surname].filter(Boolean).join(' ').trim() || nextName
        savedEmail = profile.email || user.email
      }

      if (pendingFile) {
        const uploaded = await uploadProfilePictureFile(requestUserId, pendingFile)
        ensureCurrentAccount()
        uploadedPath = uploaded.path
        const profile = await updateProfilePicture(uploaded.path, {
          expectedUserId: requestUserId,
        })
        ensureCurrentAccount()
        savedAvatarPath = profile.profilePicturePath ?? uploaded.path
        savedAvatar = uploaded.signedUrl
        if (user.avatarPath && user.avatarPath !== savedAvatarPath) {
          void deleteProfilePictureFile(user.avatarPath, { expectedUserId: requestUserId })
        }
      } else if (avatarPath === null && user.avatarPath) {
        const profile = await removeProfilePicture({ expectedUserId: requestUserId })
        ensureCurrentAccount()
        savedAvatarPath = profile.profilePicturePath ?? null
        savedAvatar = providerAvatar
        void deleteProfilePictureFile(user.avatarPath, { expectedUserId: requestUserId })
      }

      ensureCurrentAccount()
      onSave({
        name: savedName,
        email: savedEmail,
        avatar: savedAvatar,
        avatarPath: savedAvatarPath,
        newsletter: user.newsletter,
      })
      setName(savedName)
      setAvatar(savedAvatar)
      setAvatarPath(savedAvatarPath)
      setPendingFile(null)
      showSaved()
    } catch {
      if (uploadedPath) {
        void deleteProfilePictureFile(uploadedPath, { expectedUserId: requestUserId })
      }
      setError('Could not save your changes. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const saveNewsletter = async (nextNewsletter: boolean) => {
    const previousNewsletter = newsletter
    setNewsletter(nextNewsletter)
    setNewsletterSaving(true)
    setNewsletterSaved(false)
    setNewsletterError(null)
    try {
      await onNewsletterChange(nextNewsletter)
      showNewsletterSaved()
    } catch {
      setNewsletter(previousNewsletter)
      setNewsletterError('Could not update newsletter settings. Please try again.')
    } finally {
      setNewsletterSaving(false)
    }
  }

  const saveCurrency = async () => {
    const nextCurrency = selectedCurrency.trim().toUpperCase()
    setCurrencySaving(true)
    setCurrencySaved(false)
    setCurrencyError(null)
    try {
      await onCurrencyChange(nextCurrency)
      if (activeUserIdRef.current !== userId) return
      setCurrencySaved(true)
    } catch {
      if (activeUserIdRef.current !== userId) return
      setCurrencyError('Could not update your currency. Please try again.')
    } finally {
      if (activeUserIdRef.current === userId) {
        setCurrencySaving(false)
      }
    }
  }

  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Account"
        title="Your account"
        sub="Your name and photo are how you show up across Meant. This is separate from your shopping preferences."
        right={
          <button className="mt-act mt-act-primary mt-prefs-done" type="button" onClick={onDone}>
            Done
          </button>
        }
      />
      <div className="mt-acct-card">
        <div className="mt-acct-idrow">
          <div className="mt-acct-avatar">
            <Avatar user={preview} size={84} />
          </div>
          <div className="mt-acct-photo-actions">
            <button
              className="mt-acct-uploadbtn"
              type="button"
              onClick={() => fileRef.current?.click()}
            >
              {hasProfilePicture ? 'Change photo' : 'Upload photo'}
            </button>
            {hasCustomProfilePicture ? (
              <button
                className="mt-acct-removebtn"
                type="button"
                onClick={() => {
                  setSaved(false)
                  setPendingFile(null)
                  setAvatar(providerAvatar)
                  setAvatarPath(null)
                }}
              >
                Remove
              </button>
            ) : null}
            <div className="mt-acct-photo-hint">
              JPG, PNG, or WebP up to 5 MB. A square image works best.
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={onFile}
              hidden
            />
          </div>
        </div>
        <div className="mt-acct-fields">
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Full name</span>
            <input
              className="mt-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Email</span>
            <input className="mt-input" type="email" value={user.email} readOnly disabled />
            <span className="mt-field-hint">
              Your email is your sign-in identity and can't be changed here.
            </span>
          </label>
        </div>
        <div className="mt-acct-save-row">
          <button
            className="mt-acct-save"
            type="button"
            disabled={!dirty || saving}
            onClick={() => {
              void saveAccount()
            }}
          >
            {saving ? 'Saving…' : 'Save changes'}
          </button>
          {saved ? <span className="mt-acct-saved-note">Saved</span> : null}
          {error ? <span className="mt-acct-save-error">{error}</span> : null}
        </div>
      </div>
      <section className="mt-acct-card mt-acct-currency">
        <div>
          <div className="mt-acct-link-t">Price currency</div>
          <div className="mt-acct-link-s">
            Meant requests and compares catalog prices in this currency. USD is used by default.
          </div>
        </div>
        <div className="mt-acct-currency-control">
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Currency</span>
            <select
              className="mt-select"
              value={selectedCurrency}
              disabled={currencySaving}
              onChange={(event) => {
                setSelectedCurrency(event.target.value)
                setCurrencySaved(false)
                setCurrencyError(null)
              }}
            >
              {!CURRENCY_OPTIONS.some(([code]) => code === selectedCurrency) ? (
                <option value={selectedCurrency}>{selectedCurrency}</option>
              ) : null}
              {CURRENCY_OPTIONS.map(([code, label]) => (
                <option key={code} value={code}>
                  {code} — {label}
                </option>
              ))}
            </select>
          </label>
          <button
            className="mt-acct-save"
            type="button"
            disabled={selectedCurrency === currency || currencySaving}
            onClick={() => void saveCurrency()}
          >
            {currencySaving ? 'Saving…' : 'Save currency'}
          </button>
        </div>
        <div className="mt-acct-newsletter-state">
          {currencySaved ? <span className="mt-acct-saved-note">Saved</span> : null}
          {currencyError ? <span className="mt-acct-save-error">{currencyError}</span> : null}
        </div>
      </section>
      <button className="mt-acct-link" type="button" onClick={onEditPrefs}>
        <div>
          <div className="mt-acct-link-t">Shopping preferences</div>
          <div className="mt-acct-link-s">
            The filters Meant applies to everything it shows you.
          </div>
        </div>
      </button>
      <section className="mt-acct-card mt-acct-newsletter">
        <div className="mt-acct-switch-row">
          <div>
            <div className="mt-acct-link-t">Newsletter updates</div>
            <div className="mt-acct-link-s">
              Get an email when Watch and other new features are ready.
            </div>
          </div>
          <label className="mt-switch">
            <input
              className="mt-switch-input"
              type="checkbox"
              role="switch"
              checked={newsletter}
              disabled={newsletterSaving}
              onChange={(event) => {
                void saveNewsletter(event.target.checked)
              }}
              aria-label="Newsletter updates"
            />
            <span className="mt-switch-track" aria-hidden="true">
              <span className="mt-switch-thumb" />
            </span>
          </label>
        </div>
        <div className="mt-acct-newsletter-state">
          {newsletterSaving ? <span className="mt-mono">Saving</span> : null}
          {newsletterSaved ? <span className="mt-acct-saved-note">Saved</span> : null}
          {newsletterError ? <span className="mt-acct-save-error">{newsletterError}</span> : null}
        </div>
      </section>
      <section className="mt-acct-card mt-acct-links">
        <div className="mt-acct-link-head">
          <div>
            <div className="mt-acct-link-t">Connected stores</div>
            <div className="mt-acct-link-s">
              Link supported merchant accounts so Meant can use scoped order, account, and loyalty
              access.
            </div>
          </div>
          {merchantIdentityLinksLoading ? (
            <span className="mt-mono mt-acct-link-status">Loading</span>
          ) : null}
        </div>
        {merchantIdentityLinksError ? (
          <div className="mt-acct-save-error">{merchantIdentityLinksError}</div>
        ) : null}
        {linkableMerchants.length > 0 ? (
          <div className="mt-acct-store-list">
            {linkableMerchants.map((merchant) => {
              const link = identityLinksByMerchant.get(merchant.id)
              const connected = link?.status === 'CONNECTED'
              return (
                <div className="mt-acct-store" key={merchant.id}>
                  <div>
                    <div className="mt-acct-store-name">
                      {merchantDisplayOrigin(merchant.domain)}
                    </div>
                    <div className="mt-mono mt-acct-store-meta">
                      {merchant.domain} ·{' '}
                      {connected
                        ? 'Connected'
                        : link?.status === 'PENDING'
                          ? 'Pending consent'
                          : 'Not connected'}
                    </div>
                  </div>
                  {connected ? (
                    <button
                      className="mt-acct-removebtn"
                      type="button"
                      onClick={() => onRevokeMerchant(merchant.id)}
                    >
                      Revoke
                    </button>
                  ) : (
                    <button
                      className="mt-acct-uploadbtn"
                      type="button"
                      onClick={() => onConnectMerchant(merchant)}
                    >
                      Connect
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        ) : merchantIdentityLinksLoading ? null : (
          <div className="mt-acct-empty">
            No listed merchants currently advertise identity linking.
          </div>
        )}
      </section>
      <div className="mt-acct-danger">
        <div>
          <div className="mt-acct-link-t">Sign out</div>
          <div className="mt-acct-link-s">
            You will need your email and password to sign back in.
          </div>
        </div>
        <button className="mt-acct-signout" type="button" onClick={onSignOut}>
          Sign out
        </button>
      </div>
    </main>
  )
}
