import { useState } from 'react'

import { CloseIcon } from '../shared/ui'
import { ComingSoonNewsletter } from './ComingSoonNewsletter'
import type { DiscoverChatThread } from './types'

const SHARE_LINK = 'app.meant.com/s/coming_soon'

const SHARE_PEOPLE = [
  { name: 'Mia', avatar: '/assets/share-mia.png' },
  { name: 'Sofia', avatar: '/assets/share-sofia.png' },
  { name: 'Olivia', avatar: '/assets/share-olivia.png' },
] as const

export function DiscoverShareSheet({
  thread,
  newsletter,
  newsletterPending,
  onClose,
  onSend,
  onNewsletterSignup,
}: Readonly<{
  thread: DiscoverChatThread
  newsletter: boolean
  newsletterPending: boolean
  onClose: () => void
  onSend: (person: string) => void
  onNewsletterSignup: () => void
}>) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(SHARE_LINK).catch(() => undefined)
    }
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1600)
  }

  return (
    <div className="mt-modal-root open" role="dialog" aria-label="Share for a second opinion">
      <button className="mt-modal-scrim" type="button" aria-label="Close" onClick={onClose} />
      <div className="mt-ct-sharesheet">
        <button className="mt-modal-close" type="button" onClick={onClose} aria-label="Close">
          <CloseIcon size={14} />
        </button>
        <div className="mt-ct-share-eyebrow mt-mono">Second opinion</div>
        <h3 className="mt-ct-share-title">Ask someone you trust</h3>
        <p className="mt-ct-share-sub">
          Send <b>"{thread.title}"</b> to a friend or partner. They can see your picks and vote
          before you buy - no account, no sign-up.
        </p>
        <div className="mt-ct-share-linkrow">
          <span className="mt-ct-share-link mt-mono">{SHARE_LINK}</span>
          <button
            className={`mt-ct-share-copy ${copied ? 'done' : ''}`}
            type="button"
            onClick={copy}
          >
            {copied ? 'Copied' : 'Copy link'}
          </button>
        </div>
        <div className="mt-ct-share-or">
          <span>or send straight to</span>
        </div>
        <div className="mt-ct-share-people">
          {SHARE_PEOPLE.map((person) => (
            <button
              key={person.name}
              className="mt-ct-share-person"
              type="button"
              onClick={() => onSend(person.name)}
            >
              <img className="mt-ct-share-person-av" src={person.avatar} alt="" />
              <span>{person.name}</span>
            </button>
          ))}
        </div>
        <div className="mt-ct-share-or">
          <span>Coming soon</span>
        </div>
        <ComingSoonNewsletter
          newsletter={newsletter}
          newsletterPending={newsletterPending}
          onNewsletterSignup={onNewsletterSignup}
        />
      </div>
    </div>
  )
}
