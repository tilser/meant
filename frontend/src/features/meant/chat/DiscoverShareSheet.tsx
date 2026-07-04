import { useState } from 'react'

import { CloseIcon } from '../shared/ui'
import type { DiscoverChatThread } from './types'

export function DiscoverShareSheet({
  thread,
  onClose,
  onSend,
}: Readonly<{
  thread: DiscoverChatThread
  onClose: () => void
  onSend: (person: string) => void
}>) {
  const [copied, setCopied] = useState(false)
  const slug = thread.title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 14)
  const link = `https://meant.app/s/${thread.id}-${slug || 'chat'}`
  const people = [
    { name: 'Alex', initial: 'A' },
    { name: 'Sam', initial: 'S' },
    { name: 'Jordan', initial: 'J' },
  ]
  const copy = () => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(link).catch(() => undefined)
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
          <span className="mt-ct-share-link mt-mono">{link}</span>
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
          {people.map((person) => (
            <button
              key={person.name}
              className="mt-ct-share-person"
              type="button"
              onClick={() => onSend(person.name)}
            >
              <span className="mt-ct-share-person-av">{person.initial}</span>
              <span>{person.name}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
