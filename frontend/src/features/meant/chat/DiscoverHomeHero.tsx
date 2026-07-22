import { useEffect, useRef, useState } from 'react'

import type { MerchantProfile } from '../../../lib/apiClient'
import type { AskReplyDraft } from '../ask/types'
import { PROFILE } from '../data'
import { CloseIcon, SparkMark } from '../shared/ui'
import { DiscoverThreadHistoryButton } from './DiscoverThreadHistoryButton'
import { MerchantScope } from './MerchantScope'
import type { DiscoverChatThread } from './types'

export function DiscoverHomeHero({
  profile,
  greeting,
  prompts,
  onSubmit,
  loading,
  merchants,
  selectedMerchantId,
  merchantsLoading,
  merchantsError,
  onMerchant,
  historyThreads,
  activeThreadId,
  onHistorySelect,
  onHistoryDelete,
  replyDraft,
  onClearReply,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  prompts: readonly string[]
  onSubmit: (query: string) => void
  loading: boolean
  merchants: readonly MerchantProfile[]
  selectedMerchantId: string | null
  merchantsLoading: boolean
  merchantsError: string | null
  onMerchant: (merchantId: string | null) => void
  historyThreads: readonly DiscoverChatThread[]
  activeThreadId: string
  onHistorySelect: (threadId: string) => void
  onHistoryDelete: (threadId: string) => void
  replyDraft: AskReplyDraft | null
  onClearReply: () => void
}>) {
  const [value, setValue] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const submittedTimeoutRef = useRef<number | null>(null)
  const hasSearchText = value.trim().length > 0

  useEffect(
    () => () => {
      if (submittedTimeoutRef.current !== null) {
        window.clearTimeout(submittedTimeoutRef.current)
      }
    },
    [],
  )

  useEffect(() => {
    if (!replyDraft) {
      return
    }
    setValue(replyDraft.suggestedText)
    inputRef.current?.focus()
  }, [replyDraft])

  const submit = (text?: string) => {
    if (loading) {
      return
    }
    const query = (text ?? value).trim()
    if (!query) {
      return
    }
    setSubmitted(true)
    if (submittedTimeoutRef.current !== null) {
      window.clearTimeout(submittedTimeoutRef.current)
    }
    submittedTimeoutRef.current = window.setTimeout(() => {
      setSubmitted(false)
      submittedTimeoutRef.current = null
    }, 520)
    setValue('')
    onClearReply()
    onSubmit(query)
  }

  return (
    <header className="mt-hero">
      <div className="mt-mono mt-hero-eyebrow">
        {greeting}, {profile.name}
      </div>
      <h1 className="mt-hero-title">
        Everything here is <em>Meant</em> for you.
      </h1>
      <p className="mt-hero-sub">
        Ask for products across supported merchants. Meant already knows you prefer{' '}
        {profile.summary}
      </p>
      {replyDraft ? (
        <div className="mt-ask-replyto mt-hero-replyto">
          <span className="mt-ask-replyto-bar" />
          <span className="mt-ask-replyto-body">
            <span className="mt-mono mt-ask-replyto-key">{replyDraft.label}</span>
            <span className="mt-ask-replyto-text">{replyDraft.text}</span>
          </span>
          <button
            className="mt-ask-replyto-x"
            type="button"
            onClick={onClearReply}
            aria-label="Cancel insight reply"
          >
            <CloseIcon size={11} />
          </button>
        </div>
      ) : null}
      <form
        className={`mt-search${hasSearchText ? ' mt-search-writing' : ''}${submitted ? ' mt-search-submitted' : ''}`}
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <span className="mt-search-spark" aria-hidden>
          <SparkMark size={20} />
        </span>
        <input
          ref={inputRef}
          className="mt-search-input"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder='Search with Meant - "a good cotton T-shirt under $50"'
          disabled={loading}
        />
        <button type="submit" className="mt-search-go" aria-label="Ask" disabled={loading}>
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
            <path
              d="M3.5 9h11M9.5 4l5 5-5 5"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </form>
      <div className="mt-hero-context">
        <MerchantScope
          merchants={merchants}
          selectedMerchantId={selectedMerchantId}
          loading={merchantsLoading}
          error={merchantsError}
          onMerchant={onMerchant}
        />
        <DiscoverThreadHistoryButton
          threads={historyThreads}
          activeId={activeThreadId}
          onSelect={onHistorySelect}
          onDelete={onHistoryDelete}
        />
      </div>
      <div className="mt-prompts">
        {prompts.map((prompt) => (
          <button
            key={prompt}
            type="button"
            className="mt-prompt"
            onClick={() => submit(prompt)}
            disabled={loading}
          >
            {prompt}
          </button>
        ))}
      </div>
    </header>
  )
}
