import { type CSSProperties, useEffect, useRef, useState } from 'react'

import { SparkMark } from '../shared/ui'
import {
  askComposerSubmissionAccepted,
  askComposerSuggestionValue,
  type AskComposerSubmitHandler,
} from './askSubmission'
import type { AskReplyDraft } from './types'

export function AskComposer({
  placeholder,
  suggestions,
  suggestionValues = [],
  showChips,
  onAsk,
  running = false,
  onStop,
  autoFocus = false,
  disabled = false,
  replyDraft = null,
  onClearReply,
}: Readonly<{
  placeholder: string
  suggestions: readonly string[]
  suggestionValues?: readonly string[]
  showChips: boolean
  onAsk: AskComposerSubmitHandler
  running?: boolean
  onStop?: () => void | Promise<void>
  autoFocus?: boolean
  disabled?: boolean
  replyDraft?: AskReplyDraft | null
  onClearReply?: () => void
}>) {
  const [value, setValue] = useState('')
  const [sentPulse, setSentPulse] = useState(false)
  const [sending, setSending] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const sentPulseTimeoutRef = useRef<number | null>(null)

  useEffect(() => {
    if (autoFocus) {
      inputRef.current?.focus()
    }
  }, [autoFocus])

  useEffect(() => {
    if (!replyDraft) {
      return
    }
    setValue(replyDraft.suggestedText)
    inputRef.current?.focus()
  }, [replyDraft])

  useEffect(
    () => () => {
      if (sentPulseTimeoutRef.current !== null) {
        window.clearTimeout(sentPulseTimeoutRef.current)
      }
    },
    [],
  )

  const send = async (text?: string) => {
    if (disabled || sending || running) {
      return
    }
    const question = (text ?? value).trim()
    if (!question) {
      return
    }
    setSending(true)
    const accepted = await askComposerSubmissionAccepted(onAsk, question)
    setSending(false)
    if (!accepted) {
      inputRef.current?.focus()
      return
    }
    setSentPulse(true)
    if (sentPulseTimeoutRef.current !== null) {
      window.clearTimeout(sentPulseTimeoutRef.current)
    }
    sentPulseTimeoutRef.current = window.setTimeout(() => {
      setSentPulse(false)
      sentPulseTimeoutRef.current = null
    }, 520)
    setValue('')
    onClearReply?.()
  }

  const hasValue = value.trim().length > 0
  const busy = disabled || sending
  const submissionBlocked = busy || running
  const canSend = hasValue && !submissionBlocked

  return (
    <div className={`mt-ask-composer ${busy ? 'mt-ask-composer-disabled' : ''}`}>
      {showChips && suggestions.length > 0 ? (
        <div className="mt-ask-chips">
          {suggestions.map((suggestion, index) => (
            <button
              key={suggestion}
              className="mt-ask-chip"
              type="button"
              onClick={() =>
                void send(askComposerSuggestionValue(suggestion, index, suggestionValues))
              }
              disabled={submissionBlocked}
              style={{ '--mt-chip-index': index } as CSSProperties}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
      {replyDraft ? (
        <div className="mt-ask-replyto">
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
            <svg width="11" height="11" viewBox="0 0 12 12" aria-hidden>
              <path
                d="m3 3 6 6m0-6L3 9"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </div>
      ) : null}
      <form
        className={`mt-ask-bar ${hasValue ? 'mt-ask-writing' : ''} ${sentPulse ? 'mt-ask-sent' : ''} ${submissionBlocked ? 'mt-ask-busy' : ''}`}
        onSubmit={(event) => {
          event.preventDefault()
          void send()
        }}
      >
        <span className="mt-ask-spark">
          <SparkMark size={17} />
        </span>
        <input
          ref={inputRef}
          className="mt-ask-input"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          disabled={busy}
          aria-label="Message Meant"
        />
        {running ? (
          <button
            type="button"
            className="mt-ask-go mt-ask-stop"
            aria-label="Stop"
            disabled={!onStop}
            onClick={() => void onStop?.()}
          >
            <svg width="16" height="16" viewBox="0 0 18 18" aria-hidden>
              <rect x="5" y="5" width="8" height="8" rx="1.5" fill="currentColor" />
            </svg>
          </button>
        ) : (
          <button
            type="submit"
            className="mt-ask-go mt-ask-send"
            aria-label="Ask"
            disabled={!canSend}
          >
            <svg width="16" height="16" viewBox="0 0 18 18" fill="none" aria-hidden>
              <path
                d="M3.5 9h11M9.5 4l5 5-5 5"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        )}
      </form>
    </div>
  )
}
