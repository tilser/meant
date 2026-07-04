import { useEffect, useMemo, useRef, useState } from 'react'

import { HistoryIcon } from '../shared/icons'
import type { DiscoverChatThread } from './types'
import {
  discoverThreadMessageCount,
  discoverThreadPreview,
  discoverThreadTime,
  discoverThreadTimeLabel,
} from './utils'

export function DiscoverThreadHistoryButton({
  threads,
  activeId,
  onSelect,
}: Readonly<{
  threads: readonly DiscoverChatThread[]
  activeId?: string
  onSelect: (threadId: string) => void
}>) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement | null>(null)
  const historyThreads = useMemo(
    () =>
      threads
        .map((thread, index) => ({ thread, index, time: discoverThreadTime(thread) ?? 0 }))
        .sort((left, right) => right.time - left.time || left.index - right.index)
        .map(({ thread }) => thread),
    [threads],
  )

  useEffect(() => {
    if (!open) {
      return undefined
    }
    const onDown = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (historyThreads.length === 0) {
    return null
  }

  return (
    <div className="mt-ct-history-wrap" ref={ref}>
      <button
        className={`mt-ct-tabtool ${open ? 'on' : ''}`}
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-haspopup="dialog"
        title="Open chat history"
      >
        <HistoryIcon size={15} />
        History
        <span className="mt-ct-history-badge">{historyThreads.length}</span>
      </button>
      {open ? (
        <div className="mt-ct-history-pop" role="dialog" aria-label="Chat history">
          <div className="mt-ct-history-head">
            <span>Chat history</span>
            <span>{historyThreads.length} saved</span>
          </div>
          <div className="mt-ct-history-list">
            {historyThreads.map((thread) => (
              <button
                key={thread.id}
                className={`mt-ct-history-row ${thread.id === activeId ? 'active' : ''}`}
                type="button"
                onClick={() => {
                  onSelect(thread.id)
                  setOpen(false)
                }}
              >
                <span className="mt-ct-history-main">
                  <span className="mt-ct-history-title">{thread.title}</span>
                  <span className="mt-ct-history-preview">{discoverThreadPreview(thread)}</span>
                </span>
                <span className="mt-ct-history-meta">
                  <span>{discoverThreadTimeLabel(thread)}</span>
                  <span>{discoverThreadMessageCount(thread)}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}
