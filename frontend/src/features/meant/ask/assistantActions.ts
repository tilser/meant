import type {
  UserAssistantConversationProfile,
  UserAssistantConversationSummaryProfile,
} from '../../../lib/apiClient'
import type { Message } from '../ask/types'
import type { AssistantProductAction } from '../chat/types'
import type { Preference, Product } from '../types'
import { productFromSearchResult } from '../product/productSearchMapping'

function assistantMessageFromProfile(
  message: UserAssistantConversationProfile['messages'][number],
  preferences: readonly Preference[],
): Message {
  return {
    role: message.role === 'assistant' ? 'ai' : 'you',
    text: message.content,
    products: (message.products ?? []).map((product) =>
      productFromSearchResult(product, preferences),
    ),
  }
}

export function messagesFromAssistantConversation(
  conversation: UserAssistantConversationProfile,
  preferences: readonly Preference[],
): Message[] {
  return (conversation.messages ?? []).map((message) =>
    assistantMessageFromProfile(message, preferences),
  )
}

export function assistantConversationSummary(
  conversation: UserAssistantConversationProfile,
): UserAssistantConversationSummaryProfile | null {
  if (!conversation.conversationId) {
    return null
  }
  const timestamp = new Date().toISOString()
  return {
    conversationId: conversation.conversationId,
    title: conversation.title?.trim() || 'New chat',
    createdAt: conversation.createdAt ?? timestamp,
    updatedAt: conversation.updatedAt ?? conversation.createdAt ?? timestamp,
  }
}

export function upsertAssistantConversationSummary(
  history: readonly UserAssistantConversationSummaryProfile[],
  summary: UserAssistantConversationSummaryProfile,
): UserAssistantConversationSummaryProfile[] {
  const existing = history.find(
    (conversation) => conversation.conversationId === summary.conversationId,
  )
  const finalSummary = existing ? { ...summary, createdAt: existing.createdAt } : summary
  return [
    finalSummary,
    ...history.filter((conversation) => conversation.conversationId !== summary.conversationId),
  ].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
}

export function askConversationDateLabel(updatedAt: string): string {
  const date = new Date(updatedAt)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
  }).format(date)
}

export function findLastAssistantMessageIndex(messages: readonly Message[]): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index]?.role === 'ai') {
      return index
    }
  }
  return -1
}

function normalizeAssistantActionText(value: string): string {
  return value
    .toLowerCase()
    .replace(/[\u2018\u2019]/g, "'")
    .replace(/\s+/g, ' ')
    .trim()
}

function latestAssistantProductList(messages: readonly Message[]): readonly Product[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]
    if (message?.role === 'ai' && message.products && message.products.length > 0) {
      return message.products
    }
  }
  return []
}

function assistantProductTargetIndex(text: string, productCount: number): number | null {
  if (productCount <= 0) {
    return null
  }
  if (/\b(first|1st|top|best)\b/.test(text)) {
    return 0
  }
  if (/\b(second|2nd)\b/.test(text)) {
    return productCount > 1 ? 1 : null
  }
  if (/\b(third|3rd)\b/.test(text)) {
    return productCount > 2 ? 2 : null
  }
  if (/\b(fourth|4th)\b/.test(text)) {
    return productCount > 3 ? 3 : null
  }
  if (/\blast\b/.test(text)) {
    return productCount - 1
  }
  if (productCount === 1 && /\b(it|this|that|one|item|product)\b/.test(text)) {
    return 0
  }
  return null
}

export function resolveAssistantProductAction(
  question: string,
  messages: readonly Message[],
): AssistantProductAction | null {
  const products = latestAssistantProductList(messages)
  if (products.length === 0) {
    return null
  }

  const text = normalizeAssistantActionText(question)
  const shouldAddToCart =
    /\b(add|put|place)\b.*\b(cart|bag)\b/.test(text) ||
    /\b(cart|bag)\b.*\b(add|put|place)\b/.test(text)
  const shouldOpen = /\b(open|view)\b/.test(text) || /\bdetails?\b/.test(text)
  if (!shouldAddToCart && !shouldOpen) {
    return null
  }

  const targetIndex = assistantProductTargetIndex(text, products.length)
  if (targetIndex === null) {
    return null
  }

  return {
    product: products[targetIndex],
    shouldAddToCart,
    shouldOpen,
  }
}
