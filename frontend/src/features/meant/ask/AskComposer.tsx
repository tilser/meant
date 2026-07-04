import { type CSSProperties, useEffect, useRef, useState } from 'react'

import { SparkMark } from '../shared/ui'

export function AskComposer({
  placeholder,
  suggestions,
  showChips,
  onAsk,
  autoFocus = false,
  disabled = false,
}: Readonly<{
  placeholder: string
  suggestions: readonly string[]
  showChips: boolean
  onAsk: (question: string) => void
  autoFocus?: boolean
  disabled?: boolean
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
          aria-label="Ask Meant message"
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
