import { type CSSProperties, type ReactNode, useEffect, useRef } from 'react'

import { ProductArtwork, SparkMark } from '../shared/ui'
import type { Product } from '../types'
import { money } from '../utils'
import type { Message } from './types'

function renderMarkdownInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = []
  const pattern = /(\*\*[^*]+\*\*|\*[^*]+\*)/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index))
    }
    const token = match[0]
    const key = `${keyPrefix}-${match.index}`
    if (token.startsWith('**') && token.endsWith('**')) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>)
    } else {
      nodes.push(<em key={key}>{token.slice(1, -1)}</em>)
    }
    lastIndex = match.index + token.length
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex))
  }
  return nodes
}

function renderAssistantMarkdown(text: string, pending?: boolean): ReactNode {
  if (!text) {
    return pending ? 'Thinking...' : ''
  }

  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index].trim()
    if (!line) {
      index += 1
      continue
    }

    const orderedMatch = line.match(/^\d+\.\s+(.+)$/)
    if (orderedMatch) {
      const items: ReactNode[] = []
      while (index < lines.length) {
        const itemMatch = lines[index].trim().match(/^\d+\.\s+(.+)$/)
        if (!itemMatch) {
          if (!lines[index].trim()) {
            index += 1
            continue
          }
          break
        }
        items.push(
          <li key={`ol-${blocks.length}-${items.length}`}>
            {renderMarkdownInline(itemMatch[1], `ol-${blocks.length}-${items.length}`)}
          </li>,
        )
        index += 1
      }
      blocks.push(<ol key={`block-${blocks.length}`}>{items}</ol>)
      continue
    }

    const bulletMatch = line.match(/^[-*]\s+(.+)$/)
    if (bulletMatch) {
      const items: ReactNode[] = []
      while (index < lines.length) {
        const itemMatch = lines[index].trim().match(/^[-*]\s+(.+)$/)
        if (!itemMatch) {
          if (!lines[index].trim()) {
            index += 1
            continue
          }
          break
        }
        items.push(
          <li key={`ul-${blocks.length}-${items.length}`}>
            {renderMarkdownInline(itemMatch[1], `ul-${blocks.length}-${items.length}`)}
          </li>,
        )
        index += 1
      }
      blocks.push(<ul key={`block-${blocks.length}`}>{items}</ul>)
      continue
    }

    const paragraphLines: string[] = []
    while (index < lines.length) {
      const current = lines[index].trim()
      if (!current || current.match(/^\d+\.\s+.+$/) || current.match(/^[-*]\s+.+$/)) {
        break
      }
      paragraphLines.push(current)
      index += 1
    }
    blocks.push(
      <p key={`block-${blocks.length}`}>
        {renderMarkdownInline(paragraphLines.join(' '), `p-${blocks.length}`)}
      </p>,
    )
  }

  return blocks
}

function AskThinkingIndicator() {
  return (
    <span className="mt-msg-thinking" role="status" aria-label="Meant is thinking">
      <span aria-hidden="true" />
      <span aria-hidden="true" />
      <span aria-hidden="true" />
    </span>
  )
}

function renderAssistantMessageContent(message: Message): ReactNode {
  if (message.pending && !message.text) {
    return <AskThinkingIndicator />
  }

  return (
    <>
      {renderAssistantMarkdown(message.text, message.pending)}
      {message.pending ? <span className="mt-msg-cursor" aria-hidden="true" /> : null}
    </>
  )
}

export function AskThread({
  messages,
  onProductOpen,
}: Readonly<{
  messages: readonly Message[]
  onProductOpen?: (product: Product) => void
}>) {
  const endRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (endRef.current) {
      endRef.current.scrollTop = endRef.current.scrollHeight
    }
  }, [messages])

  if (messages.length === 0) {
    return null
  }

  return (
    <div className="mt-ask-thread" ref={endRef}>
      {messages.map((message, index) => {
        const streaming = message.role === 'ai' && Boolean(message.pending)
        return (
          <div
            key={`${message.role}-${index}`}
            className={`mt-msg mt-msg-${message.role} ${streaming ? 'mt-msg-streaming' : ''}`}
            style={{ '--mt-msg-index': index } as CSSProperties}
          >
            {message.role === 'ai' ? (
              <span className="mt-msg-av">
                <SparkMark size={12} />
              </span>
            ) : null}
            <div className="mt-msg-stack">
              <div className={`mt-msg-bubble ${streaming ? 'mt-msg-bubble-streaming' : ''}`}>
                {message.role === 'ai' ? renderAssistantMessageContent(message) : message.text}
              </div>
              {message.products && message.products.length > 0 ? (
                <div className="mt-msg-products">
                  {message.products.map((product, productIndex) => (
                    <button
                      key={product.id}
                      className="mt-msg-product"
                      type="button"
                      onClick={() => onProductOpen?.(product)}
                      disabled={!onProductOpen}
                      style={{ '--mt-product-index': productIndex } as CSSProperties}
                    >
                      <div className="mt-msg-product-media">
                        <ProductArtwork product={product} label={product.category.toLowerCase()} />
                      </div>
                      <span className="mt-msg-product-main">
                        <span className="mt-msg-product-name">{product.name}</span>
                        <span className="mt-mono mt-msg-product-meta">
                          {product.rankingUnavailable ? 'Match not re-ranked' : `${product.match}%`}{' '}
                          · {money(product.priceFrom)}
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          </div>
        )
      })}
    </div>
  )
}
