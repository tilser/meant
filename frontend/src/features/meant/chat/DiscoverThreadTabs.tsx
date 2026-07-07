import { useCallback, useEffect, useRef, useState } from 'react'

import { ChevronIcon, PlusIcon, ShareIcon } from '../shared/icons'
import { CloseIcon, SparkMark } from '../shared/ui'
import { DiscoverThreadHistoryButton } from './DiscoverThreadHistoryButton'
import type { DiscoverChatThread } from './types'

export function DiscoverThreadTabs({
  threads,
  activeId,
  onSelect,
  onClose,
  onNew,
  onRename,
  onShare,
  onReorder,
  onDeleteHistory,
  historyThreads,
}: Readonly<{
  threads: readonly DiscoverChatThread[]
  activeId: string
  onSelect: (threadId: string) => void
  onClose: (threadId: string) => void
  onNew: () => void
  onRename: (threadId: string, title: string) => void
  onShare: () => void
  onReorder: (fromIndex: number, toIndex: number) => void
  onDeleteHistory: (threadId: string) => void
  historyThreads?: readonly DiscoverChatThread[]
}>) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [dragId, setDragId] = useState<string | null>(null)
  const [overId, setOverId] = useState<string | null>(null)
  const [tabsOverflow, setTabsOverflow] = useState(false)
  const [canScrollTabsLeft, setCanScrollTabsLeft] = useState(false)
  const [canScrollTabsRight, setCanScrollTabsRight] = useState(false)
  const tabsScrollRef = useRef<HTMLDivElement | null>(null)

  const updateTabsScrollState = useCallback(() => {
    const element = tabsScrollRef.current
    if (!element) {
      setTabsOverflow(false)
      setCanScrollTabsLeft(false)
      setCanScrollTabsRight(false)
      return
    }
    const overflow = element.scrollWidth > element.clientWidth + 1
    const maxLeft = Math.max(0, element.scrollWidth - element.clientWidth)
    setTabsOverflow(overflow)
    setCanScrollTabsLeft(overflow && element.scrollLeft > 2)
    setCanScrollTabsRight(overflow && element.scrollLeft < maxLeft - 2)
  }, [])

  const scrollTabs = (direction: 'left' | 'right') => {
    const element = tabsScrollRef.current
    if (!element) {
      return
    }
    element.scrollBy({
      left: (direction === 'left' ? -1 : 1) * Math.max(220, element.clientWidth * 0.72),
      behavior: 'smooth',
    })
  }

  const beginEdit = (thread: DiscoverChatThread) => {
    setEditingId(thread.id)
    setDraft(thread.title)
  }
  const commitEdit = () => {
    if (!editingId) {
      return
    }
    onRename(editingId, draft.trim() || 'Untitled')
    setEditingId(null)
  }
  const dropThread = (targetId: string) => {
    if (!dragId || dragId === targetId) {
      setDragId(null)
      setOverId(null)
      return
    }
    const from = threads.findIndex((thread) => thread.id === dragId)
    const to = threads.findIndex((thread) => thread.id === targetId)
    if (from >= 0 && to >= 0) {
      onReorder(from, to)
    }
    setDragId(null)
    setOverId(null)
  }

  useEffect(() => {
    updateTabsScrollState()
    const element = tabsScrollRef.current
    if (!element) {
      return undefined
    }
    const resizeObserver = new ResizeObserver(updateTabsScrollState)
    resizeObserver.observe(element)
    const handleWheel = (event: WheelEvent) => {
      if (!tabsOverflow || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) {
        return
      }
      event.preventDefault()
      element.scrollLeft += event.deltaY
      updateTabsScrollState()
    }
    element.addEventListener('wheel', handleWheel, { passive: false })
    return () => {
      resizeObserver.disconnect()
      element.removeEventListener('wheel', handleWheel)
    }
  }, [tabsOverflow, threads, updateTabsScrollState])

  useEffect(() => {
    const activeTab = tabsScrollRef.current?.querySelector<HTMLElement>(
      '[data-active-thread="true"]',
    )
    activeTab?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
    window.requestAnimationFrame(updateTabsScrollState)
  }, [activeId, updateTabsScrollState])

  return (
    <div className="mt-ct-tabs">
      <div
        className={`mt-ct-tabs-strip ${tabsOverflow ? 'overflowing' : ''} ${
          canScrollTabsLeft ? 'can-left' : ''
        } ${canScrollTabsRight ? 'can-right' : ''}`}
      >
        <button
          className="mt-ct-tabs-arrow left"
          type="button"
          disabled={!canScrollTabsLeft}
          onClick={() => scrollTabs('left')}
          aria-label="Previous chats"
          title="Previous chats"
        >
          <ChevronIcon direction="left" size={15} />
        </button>
        <div className="mt-ct-tabs-scroll" ref={tabsScrollRef} onScroll={updateTabsScrollState}>
          {threads.map((thread) => (
            <div
              key={thread.id}
              data-thread-id={thread.id}
              data-active-thread={thread.id === activeId ? 'true' : undefined}
              draggable={editingId !== thread.id}
              className={`mt-ct-tab ${thread.id === activeId ? 'on' : ''} ${
                dragId === thread.id ? 'dragging' : ''
              } ${overId === thread.id ? 'over' : ''}`}
              onClick={() => onSelect(thread.id)}
              onDragStart={(event) => {
                setDragId(thread.id)
                event.dataTransfer.effectAllowed = 'move'
                event.dataTransfer.setData('text/plain', 'tab')
              }}
              onDragOver={(event) => {
                event.preventDefault()
                if (dragId && overId !== thread.id) {
                  setOverId(thread.id)
                }
              }}
              onDrop={(event) => {
                event.preventDefault()
                dropThread(thread.id)
              }}
              onDragEnd={() => {
                setDragId(null)
                setOverId(null)
              }}
              title="Drag to reorder your chats"
            >
              <SparkMark
                size={11}
                color={thread.id === activeId ? 'var(--accent)' : 'var(--faint)'}
              />
              {editingId === thread.id ? (
                <input
                  className="mt-ct-tab-edit"
                  autoFocus
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onClick={(event) => event.stopPropagation()}
                  onBlur={commitEdit}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      commitEdit()
                    }
                    if (event.key === 'Escape') {
                      setEditingId(null)
                    }
                  }}
                />
              ) : (
                <span
                  className="mt-ct-tab-title"
                  title="Double-click to rename this mission"
                  onDoubleClick={(event) => {
                    event.stopPropagation()
                    beginEdit(thread)
                  }}
                >
                  {thread.title}
                </span>
              )}
              <button
                className="mt-ct-tab-x"
                type="button"
                onClick={(event) => {
                  event.stopPropagation()
                  onClose(thread.id)
                }}
                aria-label="Close chat"
                title={threads.length > 1 ? 'Close this chat' : 'Close chat - back to start'}
              >
                <CloseIcon size={11} />
              </button>
            </div>
          ))}
        </div>
        <button
          className="mt-ct-tabs-arrow right"
          type="button"
          disabled={!canScrollTabsRight}
          onClick={() => scrollTabs('right')}
          aria-label="Next chats"
          title="Next chats"
        >
          <ChevronIcon direction="right" size={15} />
        </button>
      </div>
      <div className="mt-ct-tabs-right">
        <button
          className="mt-ct-tabtool"
          type="button"
          onClick={onShare}
          title="Share this chat for a second opinion"
        >
          <ShareIcon />
          Share
        </button>
        <DiscoverThreadHistoryButton
          threads={historyThreads ?? threads}
          activeId={activeId}
          onSelect={onSelect}
          onDelete={onDeleteHistory}
        />
        <button className="mt-ct-newtab" type="button" onClick={onNew}>
          <PlusIcon />
          New chat
        </button>
      </div>
    </div>
  )
}
