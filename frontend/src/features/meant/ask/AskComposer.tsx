import { type CSSProperties, useEffect, useRef, useState } from 'react'

import { SparkMark } from '../shared/ui'
import type { AskReplyDraft } from './types'

export function AskComposer({
  placeholder,
  suggestions,
  showChips,
  onAsk,
  autoFocus = false,
  disabled = false,
  replyDraft = null,
  onClearReply,
}: Readonly<{
  placeholder: string
  suggestions: readonly string[]
  showChips: boolean
  onAsk: (question: string) => void
  autoFocus?: boolean
  disabled?: boolean
  replyDraft?: AskReplyDraft | null
  onClearReply?: () => void
}>) {
  const [value, setValue] = useState('')
  const [sentPulse, setSentPulse] = useState(false)
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

  const send = (text?: string) => {
    if (disabled) {
      return
    }
    const question = (text ?? value).trim()
    if (!question) {
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
    onAsk(question)
  }

  const hasValue = value.trim().length > 0
  const canSend = hasValue && !disabled

  return (
    <div className={`mt-ask-composer ${disabled ? 'mt-ask-composer-disabled' : ''}`}>
      {showChips && suggestions.length > 0 ? (
        <div className="mt-ask-chips">
          {suggestions.map((suggestion, index) => (
            <button
              key={suggestion}
              className="mt-ask-chip"
              type="button"
              onClick={() => send(suggestion)}
              disabled={disabled}
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
        className={`mt-ask-bar ${hasValue ? 'mt-ask-writing' : ''} ${sentPulse ? 'mt-ask-sent' : ''} ${disabled ? 'mt-ask-busy' : ''}`}
        onSubmit={(event) => {
          event.preventDefault()
          send()
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
          disabled={disabled}
          aria-label="Message Meant"
        />
        <button type="submit" className="mt-ask-go" aria-label="Ask" disabled={!canSend}>
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
      </form>
    </div>
  )
}
